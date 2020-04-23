(ns bluejdbc.integrations.mysql
  (:require [bluejdbc.result-set :as rs]
            [bluejdbc.statement :as stmt]
            [java-time :as t]
            [methodical.core :as m]))

;; MySQL doesn't support OffsetTime, so convert to a LocalTime in the system timezone
(m/defmethod stmt/set-parameter! [:mysql java.time.OffsetTime]
  [stmt i t options]
  (let [t (t/local-time (t/with-offset-same-instant t (t/zone-offset)))]
    (stmt/set-parameter! stmt i t options)))

;; MySQL TIMESTAMPS are actually TIMESTAMP WITH LOCAL TIME ZONE, i.e. they are stored normalized to UTC when stored.
;; However, MySQL returns them in the session time zone in an effort to make our lives horrible.
;;
;; Check and see if the column type is `TIMESTAMP` (as opposed to `DATETIME`, which is the equivalent of
;; LocalDateTime), and normalize it to a UTC timestamp if so.
(m/defmethod rs/read-column-thunk [:mysql :type/timestamp]
  [^java.sql.ResultSet rs rsmeta ^Integer i options]
  (println ".. rs getStatement getConnection):" (.. rs getStatement getConnection)) ; NOCOMMIT
  (when-let [t (.getObject rs i java.time.LocalDateTime)]
    t
    #_(if (= (.getColumnTypeName rsmeta i) "TIMESTAMP")
        (t/with-offset-same-instant (t/offset-date-time t (t/zone-id session-time-zone)) (t/zone-offset 0))
        t)))
