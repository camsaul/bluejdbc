(ns dev
  (:require [environ.core :as env]))

(defn ns-unmap-all
  "Unmap all interned vars in a namespace. Reset the namespace to a blank slate! Perfect for when you rename everything
  and want to make sure you didn't miss a reference or when you redefine a multimethod.

    (ns-unmap-all *ns*)"
  ([]
   (ns-unmap-all *ns*))

  ([a-namespace]
   (doseq [[symb] (ns-interns a-namespace)]
     (ns-unmap a-namespace symb))))

(defn set-jdbc-url!
  "Set the JDBC URL used for testing."
  [url]
  (alter-var-root #'env/env assoc :jdbc-url url)
  nil)
