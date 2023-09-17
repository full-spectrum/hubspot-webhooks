(ns leads.plugins.hubspot.core
  (:require [leads.plugins.hubspot.webhook :as webhook]
            [leads.plugins.hubspot.config :as config]
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


(defn handle-webhook []
  (fn [req next]
    (js/console.info "Request:" (name (:request-method req)) (:uri req))
    (let [headers (:headers req)
          timestamp (get headers "x-hubspot-request-timestamp")]
      (println "timely?" (webhook/recent-request? (js/Number timestamp)))
      (println "signature given" (get headers "x-hubspot-signature-v3"))
      (println "signature calc " (webhook/request-signature secret webhook-url req))
      (next {:status 200 :body "webhook"}))))

(defonce server-ref
  (volatile! nil))

(defn start-server
  "Takes a port, starts a webserver on that port and return a Node.js
   `http.Server` object."
  [port]
  (js/console.info "Starting HTTP server...")
  (->> (http/start {:port port
                    :handler (wrap-body (handle-webhook))
                    :on-success #(js/console.info "Server started on port" port)})
       (vreset! server-ref)))

(defn stop-server
  [server on-close]
  (js/console.info "Stopping HTTP server...")
  (.close server
          (fn [err]
            (if err
              (js/console.warn "HTTP server shutdown failed" err)
              (js/console.info "HTTP server shutdown successful"))
            (on-close err))))

(defn main [& cli-args]
  (.on js/process "SIGINT" #(when-some [srv @server-ref]
                              (stop-server srv (fn [err]
                                                 (.exit js/process (if err 2 0))))))
  (try
    (let [conf (config/setup! cli-args)]
      (start-server (:port conf)))
    (catch ExceptionInfo e
      (js/console.error (ex-message e))
      (.exit js/process 1))))
