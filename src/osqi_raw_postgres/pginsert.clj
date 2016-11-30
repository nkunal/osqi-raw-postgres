(ns osqi-raw-postgres.pginsert
  (:require [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [clojure.core.async :refer [<!!]]
            [clojure.pprint :as pprint]
            [postgres.async :as pgasync])
  (:gen-class))

(defn open-postgres
  [cprops]
  (pgasync/open-db {:hostname (cprops :pg.hostname)
                    :port (cprops :pg.port)
                    :database (cprops :pg.database)
                    :username (cprops :pg.username)
                    :password (cprops :pg.password)
                    :pool-size (cprops :pg.pool-size)}))

(defn create-records
  [data-block node-key]
  (into {}
        (for [data-item data-block]
          (let [table-name (data-item "name")
                all-records (merge
                             {"h_host_uuid" node-key
                              "h_unixTime" (data-item "unixTime")
                              "h_action" (data-item "action")}
                             (data-item "columns"))]
            {table-name all-records}))))

(defn handle-kafka-batch
  "Function to insert kafka docs in postgres"
  [props json-docs]
  (doseq [item @json-docs]
    (let [data-block (item "data")
          node-key (item "node_key")
          pgdb (props :pgdb)
          records (create-records data-block node-key)]
      (doseq [[table-name record] records]
        (log/info (<!! (pgasync/insert! pgdb {:table table-name} record)))))))
