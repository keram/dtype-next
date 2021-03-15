(ns tech.v3.datatype.ffi-test
  (:require [tech.v3.datatype :as dtype]
            [tech.v3.datatype.functional :as dfn]
            [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype.native-buffer :as native-buffer]
            [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log])
  (:import [tech.v3.datatype.ffi Pointer]))


(defn generic-define-library
  []
  (let [libmem-def (dt-ffi/define-library
                     {:memset {:rettype :pointer
                               :argtypes [['buffer :pointer]
                                          ['byte-value :int32]
                                          ['n-bytes :size-t]]}
                      :memcpy {:rettype :pointer
                               ;;dst src size-t
                               :argtypes [['dst :pointer]
                                          ['src :pointer]
                                          ['n-bytes :size-t]]}
                      :qsort {:rettype :void
                              :argtypes [['data :pointer]
                                         ['nitems :size-t]
                                         ['item-size :size-t]
                                         ['comparator :pointer]]}})
        ;;nil meaning find the symbols in the current process
        libmem-inst (dt-ffi/instantiate-library libmem-def nil)
        libmem-fns @libmem-inst
        memcpy (:memcpy libmem-fns)
        memset (:memset libmem-fns)
        qsort (:qsort libmem-fns)
        comp-iface-def (dt-ffi/define-foreign-interface :int32 [:pointer :pointer])
        comp-iface-inst (dt-ffi/instantiate-foreign-interface
                         comp-iface-def
                         (fn [^Pointer lhs ^Pointer rhs]
                           (let [lhs (.getDouble (native-buffer/unsafe) (.address lhs))
                                 rhs (.getDouble (native-buffer/unsafe) (.address rhs))]
                             (Double/compare lhs rhs))))
        comp-iface-ptr (dt-ffi/foreign-interface-instance->c
                        comp-iface-def
                        comp-iface-inst)
        first-buf (dtype/make-container :native-heap :float32 (range 10))
        second-buf (dtype/make-container :native-heap :float32 (range 10))
        dbuf (dtype/make-container :native-heap :float64 (shuffle (range 100)))]
    (memset first-buf 0 40)
    (memcpy second-buf first-buf 40)
    (qsort dbuf (dtype/ecount dbuf) Double/BYTES comp-iface-ptr)
    (is (dfn/equals first-buf (vec (repeat 10 0.0))))
    (is (dfn/equals second-buf (vec (repeat 10 0.0))))
    (is (dfn/equals dbuf (range 100)))
    (is (= (.findSymbol libmem-inst "qsort")
           (.findSymbol libmem-inst "qsort")))))


(deftest jna-ffi-test
  (dt-ffi/set-ffi-impl! :jna)
  (generic-define-library))


(if (dt-ffi/jdk-ffi?)
  (deftest mmodel-ffi-test
    (dt-ffi/set-ffi-impl! :jdk)
    (generic-define-library))
  (log/warn "JDK-16 FFI pathway not tested."))


(deftest library-instance-test
  (let [library-def* (atom {:memset {:rettype :pointer
                                     :argtypes [['buffer :pointer]
                                                ['byte-value :int32]
                                                ['n-bytes :size-t]]
                                     :doc "set memory to value"}})
        singleton (dt-ffi/library-singleton library-def*)
        ;;set-library! hasn't been called
        _ (is (thrown? Exception (dt-ffi/library-singleton-find-fn singleton :memset)))
        _ (dt-ffi/library-singleton-set! singleton nil)
        _ (println @singleton)
        _ (is (dt-ffi/library-singleton-find-fn singleton :memset))
        _ (is (thrown? Exception (dt-ffi/library-singleton-find-fn singleton :memcpy)))
        _ (reset! library-def* {:memset {:rettype :pointer
                                         :argtypes [['buffer :pointer]
                                                    ['byte-value :int32]
                                                    ['n-bytes :size-t]]
                                         :doc "set memory to value"}
                                :memcpy {:rettype :pointer
                                         ;;dst src size-t
                                         :argtypes [['dst :pointer]
                                                    ['src :pointer]
                                                    ['n-bytes :size-t]]}})
        _ (dt-ffi/library-singleton-reset! singleton)
        _ (is (dt-ffi/library-singleton-find-fn singleton :memset))
        _ (is (dt-ffi/library-singleton-find-fn singleton :memcpy))]))
