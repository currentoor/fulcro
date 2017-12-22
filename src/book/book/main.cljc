(ns book.main
  (:require
    [book.macros :refer [defexample deftool]]
    #?@(:cljs [
               [book.ui.d3-example :as d3-example]
               [book.ui.focus-example :as focus-example]
               [book.ui.hover-example :as hover-example]
               [book.ui.victory-example :as victory-example]
               [book.ui.clip-tool-example :as clip-tool-example]
               [book.queries.union-example-1 :as union-example-1]
               [book.queries.union-example-2 :as union-example-2]
               book.server.morphing-example
               [book.server.ui-blocking-example :as ui-blocking]
               [fulcro-css.css :as css]
               ])
    [book.ui.example-1 :as ui-ex-1]
    [fulcro.server :as server :refer [defquery-root]]
    [fulcro.client.mutations :as m :refer [defmutation]]
    [fulcro.client.network :as fcn]
    [fulcro.client.primitives :as prim :refer [defsc]]
    [fulcro.client.dom :as dom]
    [fulcro.client.logging :as log]
    [book.example-1 :as ex1]
    [fulcro.client.data-fetch :as df]
    [fulcro.client :as fc]))

#?(:clj (def clj->js identity))

(defn raise-response
  "For om mutations, converts {'my/mutation {:result {...}}} to {'my/mutation {...}}"
  [resp]
  (reduce (fn [acc [k v]]
            (if (and (symbol? k) (not (nil? (:result v))))
              (assoc acc k (:result v))
              (assoc acc k v)))
    {} resp))

(def parser (server/fulcro-parser))

(defonce latency (atom 100))

(server/defmutation set-server-latency [{:keys [delay]}]
  (action [env]
    (when (<= 100 delay 30000)
      (reset! latency delay))
    (with-meta {:server-control/delay @latency}
      {::nodelay true})))

(defquery-root :server-control
  (value [env params]
    {:server-control/delay @latency}))

#?(:cljs
   (defrecord MockNetwork []
     fcn/FulcroNetwork
     (send [this edn ok err]
       (log/info "Server received " edn)
       (let [resp        (raise-response (parser {} edn))
             skip-delay? (and (map? resp) (some-> resp first second meta ::nodelay))]
         ; simulates a network delay:
         (if skip-delay?
           (ok resp)
           (js/setTimeout (fn []
                            (log/info "Server responded with " resp)
                            (ok resp)) @latency))))
     (start [this] this)))

(defsc ServerControl [this {:keys [:server-control/delay ui/hidden?]}]
  {:query         [:server-control/delay :ui/hidden?]
   :initial-state {:ui/hidden? true}
   :ident         (fn [] [:server-control/by-id :server])}
  (dom/div (clj->js {:style {:position        :fixed
                             :width           "180px"
                             :height          "130px"
                             :fontSize        "10px"
                             :backgroundColor :white
                             :opacity         1.0
                             :padding         "3px"
                             :border          "3px groove white"
                             :top             0
                             :right           (if hidden? "-179px" "-1px")}})
    (dom/div nil "Latency: " (dom/span nil delay))
    (dom/br nil)
    (dom/button #js {:onClick #(prim/transact! this `[(set-server-latency {:delay ~(+ delay 1000)})])} "Slower")
    (dom/button #js {:onClick #(prim/transact! this `[(set-server-latency {:delay ~(- delay 1000)})])} "Faster")
    (dom/div #js {:onClick #(m/toggle! this :ui/hidden?)
                  :style   #js {:color           "grey"
                                :backgroundColor "lightgray"
                                :padding         "5px"
                                :paddingLeft     "10px"
                                :fontSize        "14px"
                                :position        :relative
                                :opacity         1.0
                                :transform       "rotate(-90deg) translate(12px, -100px)"}}
      "Server Controls")))

(defmutation set-server-latency [{:keys [delay]}]
  (remote [{:keys [ast state]}]
    (-> ast
      (m/returning state ServerControl))))

(def ui-server-control (prim/factory ServerControl))

(defsc ServerControlRoot [this {:keys [ui/react-key server-control]}]
  {:query         [:ui/react-key {:server-control (prim/get-query ServerControl)}]
   :initial-state {:server-control {}}}
  (dom/div #js {:key react-key}
    (ui-server-control server-control)))

#?(:cljs (defonce example-server (map->MockNetwork {})))

#?(:cljs (deftool ServerControlRoot "server-controls"
           :started-callback (fn [app]
                               (df/load app :server-control ServerControl {:marker false}))
           :networking {:remote example-server}))

#?(:cljs (css/upsert-css "example-css" book.macros/ExampleRoot))
#?(:cljs (defexample "Sample Example" ex1/Root "example-1"))
#?(:cljs (defexample "D3" d3-example/Root "ui-d3"))
#?(:cljs (defexample "Input Focus and React Refs/Lifecycle" focus-example/Root "focus-example"))
#?(:cljs (defexample "Drawing in a Canvas" hover-example/Root "hover-example"))
#?(:cljs (defexample "Using External React Libraries" victory-example/Root "victory-example"))
#?(:cljs (defexample "Image Clip Tool" clip-tool-example/Root "clip-tool-example"))
#?(:cljs (defexample "Unions to Select Type" union-example-1/Root "union-example-1"))
#?(:cljs (defexample "UI Blocking" ui-blocking/Root "ui-blocking-example" :networking book.main/example-server))
