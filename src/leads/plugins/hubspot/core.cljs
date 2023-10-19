(ns leads.plugins.hubspot.core
  (:require [leads.plugins.hubspot.webhook :as webhook]
            [leads.plugins.hubspot.config :as config]
            [macchiato.server :as http]
            ["concat-stream" :as concat-stream]
            ["@google-cloud/pubsub" :as PubSub]))

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

(defn publish-fn
  "Takes a project ID and a topic ID and returns a function that publishes
   messages to that topic."
  [project-id topic-id]
  (let [client (PubSub/v1.PublisherClient.)
        topic-path (.projectTopicPath client project-id topic-id)]
    (fn publish
      [messages on-success on-error]
      (-> client
          (.publish #js {:topic topic-path
                         :messages (->> messages
                                        (map (fn [message]
                                               {:data (.from js/Buffer message)}))
                                        clj->js)})
          (.then (fn [[response]]
                   (js/console.debug "PubSub response" response)
                   (on-success)))
          (.catch (fn [err]
                    (js/console.error err)
                    (on-error)))))))

(defn split-events
  [events-json]
  (->> events-json
       (.parse js/JSON)
       (map #(.stringify js/JSON %))))

(defn handle-webhook
  [secret webhook-url publish-to-queue]
  (fn [req next raise]
    (js/console.info "Request:" (name (:request-method req)) (:uri req))
    (let [headers (:headers req)
          timestamp (get headers "x-hubspot-request-timestamp")]
      (if (and (webhook/recent-request? (js/Number timestamp))
               (= (get headers "x-hubspot-signature-v3")
                  (webhook/request-signature secret webhook-url req)))
        (publish-to-queue (split-events (:body req))
                          (fn [] (next {:status 200}))
                          (fn [] (raise {:status 500})))
        (next {:status 400})))))

(defonce server-ref
  (volatile! nil))

(defn start-server
  "Takes a port, starts a webserver on that port and return a Node.js
   `http.Server` object."
  [{:keys [port secret webhook-url pubsub]}]
  (js/console.info "Starting HTTP server...")
  (->> (http/start {:port port
                    :handler (wrap-body (handle-webhook secret webhook-url
                                                        (publish-fn (:project-id pubsub)
                                                                    (:topic-id pubsub))))
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
      (start-server conf))
    (catch ExceptionInfo e
      (js/console.error (ex-message e))
      (.exit js/process 1))
    (catch js/Error e
      (js/console.error (ex-message e))
      (.exit js/process 3))))

(defn start!
  []
  (start-server @config/store))

(defn stop!
  [done]
  (when-some [srv @server-ref]
    (stop-server srv done)))
