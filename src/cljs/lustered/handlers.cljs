(ns lustered.handlers
  (:require [re-frame.core :as r]
            [ajax.core :as ajax]
            [clojure.string :as str]))

(r/register-handler
 :request
 [r/trim-v]
 (fn [db [page-name paths {:keys [method data]} callback]]
   (ajax/ajax-request
    (cond-> {:uri (str "/admin/api/" page-name
                       (if (empty? paths) "" (str "/" (str/join "/" paths))))
             :method method
             :handler (fn [[ok? data]] (if ok? (callback data))) ;; FIXME: should handle errors in a more proper way
             :format (ajax/transit-request-format)
             :response-format (ajax/transit-response-format)}
      data (assoc :params data)))
   db))

(defn request
  ([page-name paths callback]
   (request page-name paths {:method :get} callback))
  ([page-name paths opts callback]
   (r/dispatch [:request page-name paths opts callback])))

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
 (fn [_ [page-name]]
   (request page-name ["_spec"]
            (fn [spec]
              (save :spec spec)
              (request page-name [] #(save :items (vec %)))))
   {:page-name page-name :modal-shown? false}))

(r/register-handler
 :edit-item-field
 [r/trim-v (r/path :editing-item)]
 (fn [editing-item [field value]]
   (assoc-in editing-item [:item field] value)))

(defn formatted-field? [field-name]
  (= (namespace field-name) "_formatted"))

(defn preprocess-item-fields [item]
  (reduce-kv (fn [m k v]
               (if (formatted-field? k)
                 m
                 (assoc m k (str v))))
             {}
             item))

(r/register-handler
 :request-update-item
 [r/trim-v]
 (fn [{:keys [page-name] :as db} [index item callback]]
   (let [item' (preprocess-item-fields item)]
     (request page-name [(:id item)] {:method :put :data item'} callback))
   db))

(r/register-handler
 :update-item
 [r/trim-v (r/path :items)]
 (fn [items [index item]]
   (assoc items index item)))