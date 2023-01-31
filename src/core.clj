(ns core
  (:require [reitit.core :as r]
            [ring.util.response :as response]
            [ring.adapter.jetty :as jetty]
            [reitit.ring :as ring]
            [muuntaja.core :as m]
            [reitit.ring.middleware.muuntaja :as muuntaja]))

(defonce *expression-history (atom nil))

(def symbols #{"*" "+" "/" "-" "(" ")"})

(defn parse-expression [expr]
  "Convert expression into vector with values and symbols converted"
  (->> expr
       (re-seq #"-?\d+|[*+/()]")
       (map #(if-not (symbols %)
               (parse-long %)
               (symbol %)))
       (apply vector)))

(defn parse-brackets [expr]
  "Convert brackets into collections "
  (loop [[x & xs] expr
         stack nil]
    (if (nil? x)
      (reverse stack)
      (recur xs
             (cond
               (= (symbol ")") x)
               (let [[popped [_ & remaining]] (split-at (.indexOf stack (symbol "(") ) stack)]
                 (conj remaining (reverse popped)))
               :else (conj stack x))))))

(def operator-precedence ['+ '- '* '/]) ;; lowest to highest

(defn to-postfix [infix-expr]
  (cond
    (not (seq? infix-expr)) infix-expr

    (and (seq? (first infix-expr)) (= (count infix-expr) 1))
    (to-postfix (first infix-expr))

    (empty? (rest infix-expr))
    (first infix-expr)

    :else
    (loop [operators operator-precedence]
      (if-let [op (first operators)]
        (let [operator (first operators)
              idx (.lastIndexOf infix-expr operator)]
          (if (pos? idx)
            (let [[exp1 [opt & exp2]] (split-at idx infix-expr)]
              (list opt (to-postfix exp1) (to-postfix exp2)))
            (recur (rest operators))))
        (list (to-postfix (first infix-expr))
              (to-postfix (next infix-expr)))))))

(def handler
  (ring/ring-handler
   (ring/router
    ["/"
     ["calc" {:post {:handler (fn [{{expression :expression} :body-params}]
                                (try
                                  (let [infix-expr (parse-brackets (parse-expression expression))
                                        result (eval (to-postfix infix-expr))]
                                    (swap! *expression-history conj {:expression expression :result result})
                                    (response/response {:result result}))
                                  (catch java.lang.ClassCastException ex
                                    (swap! *expression-history conj {:expression expression :error :invalid-expression})
                                    (response/response {:error :invalid-expression}))
                                  (catch java.lang.RuntimeException ex
                                    (swap! *expression-history conj {:expression expression :error :invalid-expression})
                                    (response/response {:error :invalid-expression}))))}}]
     ["history" {:get {:handler (fn [_]
                                  (response/response @*expression-history))}}]]
    {:data {:muuntaja m/instance
            :middleware [muuntaja/format-middleware]}})))

(defn start! []
  (jetty/run-jetty #'handler {:port 3002 :join? false}))

(comment

  (def server (start!))

  (.stop server)
  ;;
  ;;
  (def expression "-1 * (2 * 6 / 3)")

  (def e-2 "-1 * (2 * 6 * 3)")

  (eval (to-postfix (parse-brackets (parse-expression e-2))))

  ;; => (-1 * (2 * 6 3))

  (-1 * (2 * 6 3))
  ;;
  )
