(ns leads.plugins.hubspot.core
  (:require [macchiato.server :as http]))

(defonce server-ref
  (volatile! nil))

(defn recent-request?
  [request-timestamp]
  (< (.getTime (js/Date.))
     (+ request-timestamp (* 5 60 1000))))

(defn handle-webhook []
  (fn [req next]
    (js/console.info "Request:" (name (:request-method req)) (:uri req))
    (let [headers (:headers req)
          timestamp (get headers "x-hubspot-request-timestamp")
          signature-v3 (get headers "x-hubspot-signature-v3")]
      (println "timely?" (recent-request? (js/Number timestamp)))
      (js/console.log "timestamp" timestamp)
      (next {:status 200 :body "webhook"}))))

(defn start-server
  "Takes a port, starts a webserver on that port and return a Node.js
   `http.Server` object."
  [port]
  (js/console.info "Starting...")
  (http/start {:port port
               :handler (handle-webhook)
               :on-success #(js/console.info "Server started on port" port)}))

(defn main [& cli-args]
  (if-let [port (or (first cli-args)
                    (.-PORT (.-env js/process)))]
    (vreset! server-ref (start-server port))
    (js/console.error
     (str "Port config missing! Either provide port as first CLI argument"
          " or set environment variable PORT"))))

(defn start! []
  (main 4000))

(defn stop!
  [done]
  (js/console.warn "Stopping...")
  (when-some [srv @server-ref]
    (.close srv
            (fn [err]
              (js/console.log "Shutdown successful" err)
              (done)))))
