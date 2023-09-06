(ns leads.plugins.hubspot.webhook
  "# Validate the v3 request signature

The X-HubSpot-Signature-v3 header will be an HMAC SHA-256 hash built using
the client secret of your app combined with details of the request. It will
also include a X-HubSpot-Request-Timestamp header. When validating a request
using the X-HubSpot-Signature-v3 header, you'll need to

* [x] Reject the request if the timestamp is older than 5 minutes.
* [ ] In the request URI, decode any of the URL-encoded characters listed in
  the table below. You do not need to decode the question mark that denotes
  the beginning of the query string.
* [x] Create a utf-8 encoded string that concatenates together the following:
  requestMethod + requestUri + requestBody + timestamp. The timestamp is
  provided by the X-HubSpot-Request-Timestamp header.
* [x] Create an HMAC SHA-256 hash of the resulting string using the
  application secret as the secret for the HMAC SHA-256 function.
* [x] Base64 encode the result of the HMAC function.
* [ ] Compare the hash value to the signature. If they're equal then this
  request has been verified as originating from HubSpot. It's recommended
  that you use constant-time string comparison to guard against timing
  attacks.

  Source: https://developers.hubspot.com/docs/api/webhooks/validating-requests"
  (:require [clojure.string :as str]
            [goog.crypt :as crypt]
            [goog.crypt.base64 :as base64])
  (:import [goog.crypt Hmac Sha256]))

(def hasher (Sha256.))

(defn hmac
  [message secret]
  (let [decode crypt/stringToByteArray
        hmacer (Hmac. hasher (decode secret))]
    (.getHmac hmacer (decode message))))

(defn recent-request?
  [request-timestamp]
  (< (.getTime (js/Date.))
     (+ request-timestamp (* 5 60 1000))))

(defn request-signature
  [secret url req]
  (-> (str (str/upper-case (name (:request-method req)))
           url
           (:body req) ; requires body to be a string (not a stream which is default)
           (get-in req [:headers "x-hubspot-request-timestamp"]))
      (hmac secret)
      (base64/encodeByteArray)))
