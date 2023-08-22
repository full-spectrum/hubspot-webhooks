(ns leads.plugins.hubspot.core
  (:require ["express" :as express]
            ["http" :as http]))

(defonce server-ref
  (volatile! nil))

(defn recent-request?
  [request-timestamp]
  (< (.getTime (js/Date.))
     (+ request-timestamp (* 5 60 1000))))

(defn handle-webhook []
  (fn [req res]
    (js/console.info "Request:" (.-method req) (.-url req))
    ;; x-hubspot-signature-v3 XKyTU+WLpFN4OWYFGk2zSRUINp4P+WkTYBv6tbqEYvg=,
    ;; x-hubspot-request-timestamp 1692536808815,
    (let [headers (.-headers req)
          timestamp (aget headers "x-hubspot-request-timestamp")
          signature-v3 (aget headers "x-hubspot-signature-v3")]
      (js/console.log "timely?" (recent-request? (js/Number timestamp)))
      (js/console.log "requestBody" (type (.-body req)))
      (js/console.log "timestamp" timestamp)
      (js/console.log "signature-v3" signature-v3)
      (-> res
          (.status 200)
          ; (.set "Content-Type" "text/html")
          (.json (clj->js {:hey "ya"}))))))

(defn start-server
  "Takes a port, starts a webserver on that port and return a Node.js
   `http.Server` object."
  [port]
  (js/console.info "Starting...")
  (let [express-app (doto (new express)
                      (.use (express/json))
                      (.post "/" (handle-webhook)))]
    (-> (http/createServer express-app)
        (.listen port #(js/console.info "Server started on port" port)))))

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
