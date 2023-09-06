(ns leads.plugins.hubspot.dev
  (:require [leads.plugins.hubspot.core :as core]))

(defonce port
  (volatile! nil))

(defn start!
  []
  (core/start-server @port))

(defn stop!
  [done]
  (when-some [srv @core/server-ref]
    (vreset! port (.. srv address -port))
    (core/stop-server srv done)))

(def main core/main)
