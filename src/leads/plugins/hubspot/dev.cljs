(ns leads.plugins.hubspot.dev
  (:require [leads.plugins.hubspot.config :as config]
            [leads.plugins.hubspot.core :as core]))

(defn start!
  []
  (core/start-server (:port @config/store)))

(defn stop!
  [done]
  (when-some [srv @core/server-ref]
    (core/stop-server srv done)))

(def main core/main)
