(ns lustered.handlers
  (:require [re-frame.core :as r]
            [ajax.core :as ajax]
            [clojure.string :as str]))

(r/register-handler
 :request
 [r/trim-v]
 (fn [db [paths {:keys [method data]} callback]]
   (ajax/ajax-request
    (cond-> {:uri (str "/admin/api"
                       (if (empty? paths) "" (str "/" (str/join "/" paths))))
             :method method
             :handler (fn [[ok? data]] (if ok? (callback data))) ;; FIXME: should handle errors in a more proper way
             :format (ajax/transit-request-format)
             :response-format (ajax/transit-response-format)}
      data (assoc :params data)))
   db))

(defn request
  ([paths callback]
   (request paths {:method :get} callback))
  ([paths opts callback]
   (r/dispatch [:request paths opts callback])))

(r/register-handler
 :save
 [r/trim-v]
 (fn [db [key val]]
   (assoc db key val)))

(defn save [key val]
  (r/dispatch [:save key val]))

(r/register-handler
 :init
 [r/trim-v]
 (fn [_ [callback]]
   (callback)))

(defn init [callback]
  (r/dispatch [:init callback]))

(r/register-handler
 :fetch-items
 [r/trim-v]
 (fn [db [page-name]]
   (request [page-name] #(save :items (vec %)))
   db))

(defn fetch-items [page-name]
  (r/dispatch [:fetch-items page-name]))

(r/register-handler
 :page-init
 [r/trim-v]
 (fn [_ [page-name]]
   (request [page-name "_spec"]
            (fn [spec]
              (save :spec spec)
              (fetch-items page-name)))
   {:page-name page-name :modal-shown? false}))

(defn page-init [page-name]
  (r/dispatch [:page-init page-name]))

(r/register-handler
 :edit-item-field
 [r/trim-v (r/path :editing-item)]
 (fn [editing-item [field value]]
   (assoc-in editing-item [:item field] value)))

(defn rendered-field? [field-name]
  (= (namespace field-name) "_rendered"))

(defn preprocess-item-fields [item]
  (reduce-kv (fn [m k v]
               (if (rendered-field? k)
                 m
                 (assoc m k (str v))))
             {}
             item))

(r/register-handler
 :request-create-item
 [r/trim-v]
 (fn [{:keys [page-name] :as db} [item callback]]
   (let [item' (preprocess-item-fields item)]
     ;; FIXME: callback invocation should wait for completing fetching items
     (request [page-name] {:method :post :data item'}
              (fn [_] (fetch-items page-name) (callback)))
     db)))

(r/register-handler
 :request-update-item
 [r/trim-v]
 (fn [{:keys [page-name] :as db} [index item callback]]
   (let [item' (preprocess-item-fields item)]
     ;; FIXME: callback invocation should wait for completing fetching items
     (request [page-name (:id item)] {:method :put :data item'}
              (fn [_] (fetch-items page-name) (callback))))
   db))

(r/register-handler
 :request-delete-item
 [r/trim-v]
 (fn [{:keys [page-name] :as db} [item]]
   (request [page-name (:id item)] {:method :delete}
            (fn [_] (fetch-items page-name)))
   db))
