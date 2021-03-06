(ns bluejdbc.result-set-test
  (:require [bluejdbc.connection :as connection]
            [bluejdbc.core :as jdbc]
            [bluejdbc.result-set :as rs]
            [bluejdbc.test :as test]
            [clojure.test :refer :all]
            [java-time :as t]))

(use-fixtures :each (fn [thunk]
                      (test/with-every-test-connection
                        (thunk))))

(deftest alternative-row-transforms-test
  (jdbc/with-connection [conn (test/connection)]
    (testing "Should be able to return rows as"
      (test/with-test-data [conn :people]
        (with-open [stmt (jdbc/prepare! conn "SELECT * FROM \"people\" ORDER BY \"id\" ASC;")]
          (let [expected-rows (case (connection/db-type &conn)
                                :h2
                                [[1 "Cam" (t/offset-date-time "2020-04-21T16:56-07:00")]
                                 [2 "Sam" (t/offset-date-time "2019-01-11T15:56-08:00")]]

                                :postgresql
                                [[1 "Cam" (t/offset-date-time "2020-04-21T23:56Z")]
                                 [2 "Sam" (t/offset-date-time "2019-01-11T23:56Z")]])]
            (doseq [[description {:keys [xform expected]}]
                    {"none (vectors)"
                     {:xform    nil
                      :expected expected-rows}

                     "namespaced maps"
                     {:xform    (rs/maps :namespaced)
                      :expected (for [row expected-rows]
                                  (zipmap [:people/id :people/name :people/created_at] row))}

                     "namespaced lisp-case maps"
                     {:xform    (rs/maps :namespaced :lisp-case)
                      :expected (for [row expected-rows]
                                  (zipmap [:people/id :people/name :people/created-at] row))}

                     "results-xform should be comp-able"
                     {:xform    (fn [rs]
                                  (comp ((rs/maps :namespaced :lisp-case) rs)
                                        (map :people/id)))
                      :expected [1 2]}}]
              (testing description
                (is (= expected
                       (with-open [rs (.executeQuery stmt)]
                         (transduce (take 2) conj [] (rs/reducible-result-set rs {:results/xform xform})))))

                (testing "with-prepared-statement"
                  (is (= expected
                         (with-open [rs (.executeQuery stmt)]
                           (transduce (take 2) conj [] (rs/reducible-result-set rs {:results/xform xform}))))))))))))))

(deftest transform-column-names-test
  (jdbc/with-connection [conn (test/connection)]
    (letfn [(column-names [options]
              (set (keys (jdbc/query-one conn
                                         {:select [[1 "AbC_dEF"] [2 "ghi-JKL"]]}
                                         (merge {:honeysql/quoting :ansi}
                                                options)))))]
      (testing "sanity check"
        (is (= #{:AbC_dEF :ghi-JKL}
               (column-names nil))))

      (testing "Should be able to return"
        (testing "lower-cased identifiers"
          (is (= #{:abc_def :ghi-jkl}
                 (column-names {:results/xform (jdbc/maps :lower-case)}))))

        (testing "upper-cased identifiers"
          (is (= #{:ABC_DEF :GHI-JKL}
                 (column-names {:results/xform (jdbc/maps :upper-case)}))))

        (testing "lisp-cased identifiers"
          (is (= #{:AbC-dEF :ghi-JKL}
                 (column-names {:results/xform (jdbc/maps :lisp-case)}))))

        (testing "snake-cased identifiers"
          (is (= #{:AbC_dEF :ghi_JKL}
                 (column-names {:results/xform (jdbc/maps :snake-case)}))))

        (testing "lower lisp-cased identifiers"
          (is (= #{:abc-def :ghi-jkl}
                 (column-names {:results/xform (jdbc/maps :lower-case :lisp-case)}))))

        (testing "upper lisp-cased identifiers"
          (is (= #{:ABC-DEF :GHI-JKL}
                 (column-names {:results/xform (jdbc/maps :upper-case :lisp-case)}))))))))

(deftest time-columns-test
  (is (= [(t/local-time "16:57:09")]
         (jdbc/query-one (test/connection) ["SELECT CAST(? AS time)" (t/local-time "16:57:09")] {:results/xform nil}))))

(deftest date-columns-test
  (testing "Make sure fetching DATE columns works correctly"
    (is (= [(t/local-date "2020-04-23")]
           (jdbc/query-one (test/connection) ["SELECT CAST(? AS date)" (t/local-date "2020-04-23")] {:results/xform nil})))))

;; (deftest datetime-columns-test
;;   (testing "Make sure fetching DATETIME columns works correctly"
;;     ;; Postgres doesn't have a DATETIME type
;;     (test/exclude #{:postgresql}
;;       (is (= [(t/local-date-time "2020-04-23T16:57:09")]
;;              (jdbc/query-one (test/connection)
;;                              ["SELECT CAST(? AS datetime)" (t/local-date-time "2020-04-23T16:57:09")]
;;                              {:results/xform nil}))))))

(deftest timestamp-columns-test
  (testing "Make sure fetching TIMESTAMP (without time zone) columns works correctly"
    (is (= [(t/local-date-time "2020-04-23T16:57:09")]
           (jdbc/query-one (test/connection)
                           ["SELECT CAST(? AS timestamp)"
                            (t/local-date-time "2020-04-23T16:57:09")]
                           {:results/xform nil})))))

;; TODO -- timestamp with time zone

;; TODO -- time with time zone (for DBs that support it)
