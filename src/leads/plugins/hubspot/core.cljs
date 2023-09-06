(ns leads.plugins.hubspot.core
  (:require [leads.plugins.hubspot.webhook :as webhook]
            [macchiato.server :as http]
            ["concat-stream" :as concat-stream]))

(defn wrap-body
  [handler]
  (fn [{:keys [body] :as request} respond raise]
    (if (string? body)
      (handler request respond raise)
      (.pipe ^js body
             (concat-stream.
              (fn [body]
                (handler
                 (assoc request :body (.toString body "utf8"))
                 respond
                 raise)))))))

(def secret
  "copy secret from Hubspot developer portal")

(def webhook-url
  "copy FULL URL 'Webhooks' in Hubspot developer portal")

(defonce server-ref
  (volatile! nil))

(defn handle-webhook []
  (fn [req next]
    (js/console.info "Request:" (name (:request-method req)) (:uri req))
    (let [headers (:headers req)
          timestamp (get headers "x-hubspot-request-timestamp")]
      (println "timely?" (webhook/recent-request? (js/Number timestamp)))
      (println "signature given" (get headers "x-hubspot-signature-v3"))
      (println "signature calc " (webhook/request-signature secret webhook-url req))
      (next {:status 200 :body "webhook"}))))

(defn start-server
  "Takes a port, starts a webserver on that port and return a Node.js
   `http.Server` object."
  [port]
  (js/console.info "Starting...")
  (http/start {:port port
               :handler (wrap-body (handle-webhook))
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
