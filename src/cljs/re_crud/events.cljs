(ns re-crud.events
  (:require [re-frame.core :refer [reg-event-db dispatch reg-sub reg-event-fx]]
            [re-crud.coerce :as coerce]
            [re-crud.http-client :as client]))

(defn assoc-into-db [k]
  (fn [db [_ & args]]
    (let [ks (butlast args)
          value (last args)]
      (assoc-in db (cons k ks) value))))

(defn get-in-db [k]
  (fn [db [_ & ks]]
    (get-in db (cons k ks))))

(defn crud-load-component [{:keys [db]} [_
                                         {:keys [id events] :as component}
                                         {:keys [fetch form] :as params}]]
  (let [dispatch (if (:fetch events) {:dispatch [(:fetch events) fetch]})]
    (merge dispatch
           {:db (update-in db [:crud-components id :ui :user-input] merge form)})))

(defn register-events []
  (reg-event-fx :crud-load-component crud-load-component)
  (reg-sub :crud-service-configs (get-in-db :crud-service-configs))
  (reg-sub :crud-components (get-in-db :crud-components))

  (reg-event-db :crud-components (assoc-into-db :crud-components))

  (reg-event-db
   :crud-http-request
   (fn [db [_ id operation-id params service-name on-success]]
     (let [service-config (get-in db [:crud-service-configs service-name])
           service-host (:service-host service-config)
           {:keys [url method request-schema categories resource-type] :as operation}
           (get-in service-config [:operations operation-id])]
       (client/make-request operation-id
                            method
                            (client/make-url service-host url params)
                            (coerce/request params request-schema)
                            :on-success [:crud-received-response id on-success])
       db)))

  (reg-event-db
   :crud-received-response
   (fn [db [_ id on-success response]]
     (when on-success (dispatch [on-success response]))
     db))

  (reg-event-db
   :crud-http-fail
   (fn [db [_ operation-id status response]]
     (prn operation-id status response)
     (dispatch [:crud-notify operation-id status response])
     db)))
