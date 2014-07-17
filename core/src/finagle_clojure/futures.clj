(ns finagle-clojure.futures
  "Functions for working with com.twitter.util.Future objects.
  Futures are used to represent asynchronous operations in Finagle."
  (:refer-clojure :exclude [await ensure for map])
  (:require [finagle-clojure.scala :as scala])
  (:import [com.twitter.util Await Future]))

(defn ^Future value
  "Returns a defined Future with the constant Return value `v`.

  *Arguments*:

    * `v`: the value that the new Future should be defined with.

  *Returns*:

  A new Future that is defined with the value `v`."
  [v]
  (Future/value v))

(defn ^Future exception
  "Returns a defined Future with a Throw value of t.

  *Arguments*:

    * `t`: an instance of Throwable that the new Future should be defined with.

  *Returns*:

  A new Future that is defined with the Throw value `t`."
  [^Throwable t]
  (Future/exception t))

(defn defined?
  "Does Future `f` have a value?
  If defined? the value of Future f can be retrieved with the function [[await]].

  *Arguments*:

    * `f`: a Future

  *Returns*:

    * `true` if f has been realized (has a Return or Throw value)
    * `false` if it's still waiting for a value."
  [^Future f]
  (.isDefined f))

(defn await
  "Block until Future f is defined and return its value.
  You probably don't want to use this in production code.

  *Arguments*:

    * `f`: a Future

  *Returns*:
    The value of `f` if it was successful.
    Throws the contained Exception if `f` was unsuccessful."
  [^Future f]
  (Await/result f))

(defn ^Future flatmap*
  "Apply scala.Function1 fn1 with the value of Future f when f is defined with a value (not exception).
  fn1 should return a Future.

  *Arguments*:

    * `f`: a Future
    * `fn1`: a `scala.Function1` that will execute when the (non-error) value of f is defined.
      Its result should be a Future.

  *Returns*:

    A new Future that will be defined when `f` is defined and `fn1` has been applied to its value.
    The value of the new Future will be the result of `fn1` (which should be a Future) if `f` is successful.
    If `f` results in an error then `fn1` won't run.

  See [[scala/Function]]"
  [^Future f ^scala.Function1 fn1]
  (.flatMap f fn1))

(defmacro flatmap
  "Sugar for constructing a scala.Function1 & applying [[flatmap*]] with it.

  *Arguments*:

    * `f`: a Future
    * `param-binding`: is a vector with 1 element, the name to bind the value of Future f.
    * `body`: will execute when the (non-error) value of f is defined.
       Its result should be a Future.

  *Returns*:

    A new Future that will be defined when `f` is defined and `body` has been applied to its value.
    The value of the new Future will be the result of `body` (which should be a Future) if `f` is successful.
    If `f` results in an error then `body` won't run.

  See [[flatmap*]] & [[scala/Function]]"
  [^Future f param-binding & body]
  `(flatmap* ~f (scala/Function ~param-binding ~@body)))

(defn map*
  "Apply scala.Function1 fn1 with the value of Future f when f is defined with a value (not exception).

  *Arguments*:

    * `f`: a Future
    * `fn1`: a `scala.Function1` that will execute when the (non-error) value of f is defined.

  *Returns*:

    A new Future that will be defined when `f` is defined and `fn1` has been applied to its value.
    The value of the new Future will be the result of `fn1` if `f` is successful.
    If `f` results in an error then `fn1` won't run.

  See [[scala/Function]]"
  [^Future f ^scala.Function1 fn1]
  (.map f fn1))

(defmacro map
  "Sugar for constructing a scala.Function1 & applying [[map*]] with it.

  *Arguments*:

    * `f`: a Future
    * `param-binding`: is a vector with 1 element, the name to bind the value of Future f.
    * `body`: will execute when the (non-error) value of f is defined.

  *Returns*:

    A new Future that will be defined when `f` is defined and `body` has been applied to its value.
    The value of the new Future will be the result of `body` if `f` is successful.
    If `f` results in an error then `body` won't run.

  See [[map*]] & [[scala/Function]]"
  [^Future f  param-binding & body]
  `(map* ~f (scala/Function ~param-binding ~@body)))

;; TODO support non-future values in intermediate bindings
;; like require <- or :<- or something for future values to do flatmap
(defmacro for
  "Like a scala for comprehension with Futures,
  bindings are pairs of name future,
  body is executed when all Futures are ready.
  Bindings can refer to Futures defined before them
  (which will be realized)."
  [bindings & body]
  {:pre [(coll? bindings) (even? (count bindings))]}
  (let [[name val] (take 2 bindings)]
    (if-not (or (nil? name) (nil? val))
      `(flatmap ~val [~name]
        (for ~(drop 2 bindings) ~@body))
      `(do ~@body))))

(defn ^Future collect
  "Takes a seq of Futures, returns a Future of a seq of their values.

  *Arguments*:

    * `future-seq`: a Clojure seq of Futures

  *Returns*:

    A new Future that will be defined when all Futures in `future-seq` have been defined.
    The value of that Future will be a Clojure seq of the values of the Futures in `future-seq`."
  [future-seq]
  (flatmap (Future/collect (scala/seq->scala-buffer future-seq))
    [scala-seq]
    (value (scala/scala-seq->List scala-seq))))

(defn ^Future rescue*
  "Apply scala.PartialFunction pfn with the value of Future f when f is defined with a Throw (a Throwable).
  This is like [[flatmap*]] for when a Future results in an error.

  *Arguments*:

    * `f`: a Future
    * `pfn`: a `scala.PartialFunction` that will execute when the value of f is defined (as a Throwable).
      The return value should be a Future.

  *Returns*:

    A new Future that will be defined when `f` is defined and `pfn` has been applied to its value.
    The value of the new Future will be the result of `pfn` if `f` results in an error.
    If `f` is successful then `pfn` won't run.

  See [[scala/Function]]"
  [^Future f ^scala.PartialFunction pfn]
  (.rescue f pfn))

(defmacro rescue
  "Sugar for constructing a scala.PartialFunction & applying [[rescue*]] with it.
  This is like [[flatmap]] for when a Future results in an error.

  *Arguments*:

    * `f`: a Future
    * `param-binding`: is a vector with 1 element, the name to bind the value of Future f.
    * `body`: will execute when the (error) value of f is defined.
      The return value should be a Future.

  *Returns*:

    A new Future that will be defined when `f` is defined and `body` has been applied to its value.
    The value of the new Future will be the result of `body` if `f` results in an error.
    If `f` is successful then `body` won't run.

  See [[rescue*]] & [[scala/Function]]"
  [^Future f arg-binding & body]
  `(rescue* ~f (scala/Function ^Throwable ~arg-binding ~@body)))

(defn ^Future handle*
  "Apply scala.PartialFunction pfn with the value of Future f when f is defined with a Throw (a Throwable).
  This is like [[map*]] for when a Future results in an error.

  *Arguments*:

    * `f`: a Future
    * `pfn`: a `scala.PartialFunction` that will execute when the value of f is defined (as a Throwable).

  *Returns*:

    A new Future that will be defined when `f` is defined and `pfn` has been applied to its value.
    The value of the new Future will be the result of `pfn` if `f` results in an error.
    If `f` is successful then `pfn` won't run.

  See [[scala/Function]]"
  [^Future f ^scala.PartialFunction pfn]
  (.handle f pfn))

(defmacro handle
  "Sugar for constructing a scala.PartialFunction & applying [[handle*]] with it.
  This is like [[map]] for when a Future results in an error.

  *Arguments*:

    * `f`: a Future
    * `param-binding`: is a vector with 1 element, the name to bind the value of Future f.
    * `body`: will execute when the (error) value of f is defined.
      The return value should be a Future.

  *Returns*:

    A new Future that will be defined when `f` is defined and `body` has been applied to its value.
    The value of the new Future will be the result of `body` if `f` results in an error.
    If `f` is successful then `body` won't run.

  See [[handle*]] & [[scala/Function]]"
  [^Future f arg-binding & body]
  `(handle* ~f (scala/Function ^Throwable ~arg-binding ~@body)))

(defn ^Future ensure*
  "Apply scala.Function0 `fn0` when Future `f` is defined whether it is successful or not.
  This is primarily used for side-effects, the return value of `fn0` is ignored.

  *Arguments*:

    * `f`: a Future
    * `fn0`: a `scala.Function0` that will execute when the value of f is defined.

  *Returns*:

    A new Future that will be defined when `f` is defined and `fn0` has executed.

  See [[scala/Function0]]"
  [^Future f ^scala.Function0 fn0]
  (.ensure f fn0))

(defmacro ensure
  [^Future f & body]
  "Sugar for constructing a scala.Function0 & applying [[ensure*]] with it.
  This is primarily used for side-effects, the return value is ignored.

  *Arguments*:

    * `f`: a Future
    * `body`: will execute when the (error) value of f is defined.
      The return value will be ignored.

  *Returns*:

    A new Future that will be defined when `f` is defined and `body` has executed.

  See [[ensure*]] & [[scala/Function0]]"
  `(ensure* ~f (scala/Function0 ~@body)))

(defn select
  "*Arguments*:

    * `f1`: a Future
    * `f2`: a Future

  *Returns*:

    A new Future that will be defined with the value of the Future that returns first (between `f1` & `f2`)."
  [^Future f1 ^Future f2]
  (.select f1 f2))