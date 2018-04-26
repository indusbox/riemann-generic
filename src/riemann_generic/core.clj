(ns riemann-generic.core
  (:require [riemann.streams :refer :all]
            [riemann.config :refer :all]
            [riemann.test :refer :all]
            [riemann-cond-dt.core :as dt]
            [clojure.tools.logging :refer :all]))

(defn condition
  "Use the `:condition-fn` value (which should be function accepting an event) to set the event `:state` accordingly. Forward events to children

  `opts` keys:
  - `:condition-fn` : A function accepting an event and returning a boolean 
  - `:state`        : The state of event forwarded to children.

  Example:

  (condition {:condition-fn #(and (>= (:metric %) 30)
                                  (< (:metric %) 70))
              :state \"warning\"})

  In this example, event :state will be \"warning\" if `:metric` is >= 30 and < 70"
  [opts & children]
  (let [child-stream (remove nil?
                       [(when (:condition-fn opts)
                          (where ((:condition-fn opts) event)
                            (with :state (:state opts)
                              (fn [event]
                                (call-rescue event children)))))])]
    (apply sdo child-stream)))

(defn condition-during
  "if the condition `condition-fn`(which should be function accepting an event) is valid for all events received during at least the period `dt`, valid events received after the `dt` period will be passed on until an invalid event arrives. Forward to children.
  `:metric` should not be nil (it will produce exceptions).

  `opts` keys:
  - `:condition-fn` : A function accepting an event and returning a boolean.
  - `:duration`     : The time period in seconds.
  - `:state`        : The state of event forwarded to children.
  - `:by-fields` : An optional list of field to group events.

  Example:

  (condition-during {:condition-fn #(and 
                                      (> (:metric %) 42) 
                                      (compare (:service %) \"foo\"))
                     :duration 10
                     :state \"critical\"
                     :by-fields [:service]})

  Set `:state` to \"critical\" if events `:metric` is > to 42 and `:metric` is \"foo\" during 10 sec or more for a specific service."
  [opts & children]
  (let [by-fields (map keyword (remove nil? (or (:by-fields opts) [])))]
    (if (not-empty by-fields)
      (by by-fields
        (dt/cond-dt (:condition-fn opts) (:duration opts)
          (with :state (:state opts)
            (fn [event]
              (call-rescue event children)))))
      (dt/cond-dt (:condition-fn opts) (:duration opts)
        (with :state (:state opts)
          (fn [event]
            (call-rescue event children)))))))


(defn above
  "if the `:metric` event value is strictly superior to the values of `:threshold` in `opts` and update the event state accordingly, and forward to children.

  `opts` keys:
  - `:threshold` : A number, the event `:state` will be set to `critical` if the event metric is > to the value. 
  - `:state`     : The state of event forwarded to children.

  Example:

  (above {:threshold 30 :state \"critical\"} email)

  Set `:state` to \"critical\" if events `:metric` is > to 30."
  [opts & children]
  (apply condition {:condition-fn #(> (:metric %) (:threshold opts))
                    :state (:state opts)}
    children))

(defn above-during
  "If the condition `(> (:metric event) threshold)` is valid for all events received during at least the period `dt`, valid events received after the `dt` period will be passed on until an invalid event arrives. Forward to children.
  `:metric` should not be nil (it will produce exceptions).

  `opts` keys:
  - `:threshold` : The threshold used by the above stream
  - `:duration`  : The time period in seconds.
  - `:state`     : The state of event forwarded to children.
  - `:by-fields` : An optional list of field to group events.

  Example:

  (above-during {:threshold 70 :duration 10 :state \"critical\" :by-fields [:service]} email)

  Set `:state` to \"critical\" if events `:metric` is > to 70 during 10 sec or more for a specific service."
  [opts & children]
  (let [by-fields (map keyword (remove nil? (or (:by-fields opts) [])))]
    (if (not-empty by-fields)
      (by by-fields
        (dt/above (:threshold opts) (:duration opts)
          (with :state (:state opts)
            (fn [event]
              (call-rescue event children)))))
      (dt/above (:threshold opts) (:duration opts)
        (with :state (:state opts)
          (fn [event]
            (call-rescue event children)))))))

(defn below
  "if the `:metric` event value is strictly inferior to the values of `:threshold` in `opts` and update the event state accordingly, and forward to children.

  `opts` keys:
  - `:threshold` : A number, the event `:state` will be set to `critical` if the event metric is > to the value. 
  - `:state`     : The state of event forwarded to children.

  Example:

  (below {:threshold 30 :state \"critical\"} email)

  Set `:state` to \"critical\" if events `:metric` is < to 30."
  [opts & children]
  (apply condition {:condition-fn #(< (:metric %) (:threshold opts))
                    :state (:state opts)}
    children))

(defn below-during
  "If the condition `(< (:metric event) threshold)` is valid for all events received during at least the period `dt`, valid events received after the `dt` period will be passed on until an invalid event arrives. Forward to children.
  `:metric` should not be nil (it will produce exceptions).

  `opts` keys:
  - `:threshold` : The threshold used by the above stream
  - `:duration`  : The time period in seconds.
  - `:state`     : The state of event forwarded to children.
  - `:by-fields` : An optional list of field to group events.

  Example:

  (below-during {:threshold 70 :duration 10 :state \"critical\" :by-fields [:service]} email)

  Set `:state` to \"critical\" if events `:metric` is < to 70 during 10 sec or more for a specific service."
  [opts & children]
  (let [by-fields (map keyword (remove nil? (or (:by-fields opts) [])))]
    (if (not-empty by-fields)
      (by by-fields
        (dt/below (:threshold opts) (:duration opts)
          (with :state (:state opts)
            (fn [event]
              (call-rescue event children)))))
      (dt/below (:threshold opts) (:duration opts)
        (with :state (:state opts)
          (fn [event]
            (call-rescue event children)))))))

(defn outside
  "If the condition `(or (< (:metric event) low) (> (:metric event) high))` is valid for all events received, valid events received will be passed on until an invalid event arrives.

  `opts` keys:
  - `:min-threshold` : The min threshold
  - `:max-threshold` : The max threshold
  - `:state`         : The state of event forwarded to children.

  Example:

  (outside {:min-threshold 70
            :max-threshold 90
            :state \"critical\"})

  Set `:state` to \"critical\" if events `:metric` is < to 70 or > 90."
  [opts & children]
  (apply condition {:condition-fn #(or
                                     (< (:metric %) (:min-threshold opts))
                                     (> (:metric %) (:max-threshold opts)))
                    :state (:state opts)}
    children))

(defn outside-during
  "If the condition `(or (< (:metric event) low) (> (:metric event) high))` is valid for all events received during at least the period `dt`, valid events received after the `dt` period will be passed on until an invalid event arrives.

  `opts` keys:
  - `:min-threshold` : The min threshold
  - `:max-threshold` : The max threshold
  - `:duration`      : The time period in seconds.
  - `:state`         : The state of event forwarded to children.
  - `:by-fields`     : An optional list of field to group events.

  Example:

  (outside-during {:min-threshold 70
                   :max-threshold 90
                   :duration 10
                   :state \"critical\"
                   :by-fields [:service]})

  Set `:state` to \"critical\" if events `:metric` is < to 70 or > 90 during 10 sec or more for a specific service."
  [opts & children]
  (let [by-fields (map keyword (remove nil? (or (:by-fields opts) [])))]
    (if (not-empty by-fields)
      (by by-fields
        (dt/outside (:min-threshold opts) (:max-threshold opts) (:duration opts)
          (with :state (:state opts)
            (fn [event]
              (call-rescue event children)))))
      (dt/outside (:min-threshold opts) (:max-threshold opts) (:duration opts)
        (with :state (:state opts)
          (fn [event]
            (call-rescue event children)))))))

(defn between
  "If the condition `(and (> (:metric event) low) (< (:metric event) high))` is valid for all events received, valid events received will be passed on until an invalid event arrives.

  `:metric` should not be nil (it will produce exceptions).
  `opts` keys:
  - `:min-threshold` : The min threshold
  - `:max-threshold` : The max threshold
  - `:state`         : The state of event forwarded to children.
  - `:by-fields` : An optional list of field to group events.

  Example:

  (between-during {:min-threshold 70
                   :max-threshold 90
                   :service \"bar\"
                   :state \"critical\"
                   :by-fields [:service]})

  Set `:state` to \"critical\" if events `:metric` is > to 70 and < 90 for a specific service."
  [opts & children]
  (apply condition {:condition-fn #(and
                                     (> (:metric %) (:min-threshold opts))
                                     (< (:metric %) (:max-threshold opts)))
                    :state (:state opts)}
    children))

(defn between-during
  "If the condition `(and (> (:metric event) low) (< (:metric event) high))` is valid for all events received during at least the period `dt`, valid events received after the `dt` period will be passed on until an invalid event arrives.

  `:metric` should not be nil (it will produce exceptions).
  `opts` keys:
  - `:min-threshold` : The min threshold
  - `:max-threshold` : The max threshold
  - `:duration`      : The time period in seconds.
  - `:state`         : The state of event forwarded to children.
  - `:by-fields`     : An optional list of field to group events.

  Example:

  (between-during {:min-threshold 70
                   :max-threshold 90
                   :duration 10
                   :service \"bar\"
                   :state \"critical\"
                   :by-fields [:service]})

  Set `:state` to \"critical\" if events `:metric` is > to 70 and < 90 during 10 sec or more for a specific service."
  [opts & children]
  (let [by-fields (map keyword (remove nil? (or (:by-fields opts) [])))]
    (if (not-empty by-fields)
      (by by-fields
        (dt/between (:min-threshold opts) (:max-threshold opts) (:duration opts)
          (with :state (:state opts)
            (fn [event]
              (call-rescue event children)))))
      (dt/between (:min-threshold opts) (:max-threshold opts) (:duration opts)
        (with :state (:state opts)
          (fn [event]
            (call-rescue event children)))))))

(defn regex
  "if regex `:pattern` matched `:field` of all events received, the matched events will be passed on until an invalid event arrives. The matched event `:state` will be set to `state` and forward to children.
  if the value of `:field` of event is nil, the event will be filtered.

  `opts` keys:
  - `:pattern`  : A string regex pattern
  - `:field`    : Apply regex to field of event
  - `:state`    : The state of event forwarded to children.

  Example:

  (regex {:pattern '.*(?i)error.*'
          :field \"description\"
          :state \"critical\"}
         children)

  Set `:state` to \"critical\" if metric of events contain \"error\" in field \"description\" during 10 sec or more."
  [opts & children]
  (apply condition {:condition-fn #(and
                                     (not= ((keyword (:field opts)) %) nil)
                                     (re-matches (re-pattern (:pattern opts)) ((keyword (:field opts)) %)))
                    :state (:state opts)}
    children))

(defn regex-during
  "if regex `:pattern` matched all events received during at least the period `dt`, matched events received after the `dt` period will be passed on until an invalid event arrives. The matched event `:state` will be set to `:state` and forward to children.
  if the value of `:field` of event is nil, the event will be filtered.

  `opts` keys:
  - `:pattern`  : A string regex pattern
  - `:field`    : Apply regex to field of event
  - `:duration` : The time period in seconds.
  - `:state`    : The state of event forwarded to children.
  - `:by-fields` : An optional list of field to group events.

  Example:

  (regex-during {:pattern '.*(?i)error.*'
                 :duration 10
                 :state \"critical\"
                 :by-fields [:service]}
                children)

  Set `:state` to \"critical\" if metric of events contain \"error\" during 10 sec or more for a specific service."
  [opts & children]
  (apply condition-during {:condition-fn #(and
                                            (not= ((keyword (:field opts)) %) nil)
                                            (re-matches (re-pattern (:pattern opts)) ((keyword (:field opts)) %)))
                           :duration (:duration opts)
                           :by-fields (or (:by-fields opts) [])
                           :state (:state opts)}
    children))
(defn ddt-condition
  "Differentiate metrics with respect to time, emits a rate-of-change event every `dt` seconds, divided by the difference in their times.
  if the `condition-fn` apply to the rate-of-change event return true, update the event state and service accordingly, and forward to children.
  Skips events without metrics.

  `opts` keys:
  - `:dt`           : emits a rate-of-change event every `dt` seconds
  - `:condition-fn` : A function accepting an event and returning a boolean.
  - `:state`        : The state of event forwarded to children.

  Example:
  (ddt-condition {:dt 3
                  :condition-fn #(> (:metric %) 5)
                  :state \"warning\" })

  Set `:state` to \"warning\"  and `:service` to `ddt_`+ \"service\" of event if the function `:condition-fn` apply to the derivate of metric every 3 seconds return true"
  [opts & children]
  (ddt-real (:dt opts)
    (where ((:condition-fn opts) event)
      (fn [event]
        (let [new-event (assoc event :state (:state opts) :service (#(str "ddt_" (:service %)) event))]
          (call-rescue new-event children))))))

(defn ddt-above
  "Differentiate metrics with respect to time, emits a rate-of-change event every `dt` seconds, divided by the difference in their times. if the `:metric` rate-of-change event is superior to the threshold `threshold`, update the event state and service accordingly, and forward to children.
  Skips events without metrics.

  `opts` keys:
  - `:dt`         : emits a rate-of-change event every `dt` seconds
  - `:threshold`  : The threshold used by the above stream
  - `:state`      : The state of event forwarded to children.

  Example:
  (ddt-above {:dt 2
              :threshold 5
              :state \"critical\" }
             children)

  Set `:state` to \"critical\"  and `:service` to `ddt_`+ \"service\" of event if the derivate of metric every 2 seconds is superior to 5"
  [opts & children]
  (apply ddt-condition {:condition-fn #(> (:metric %) (:threshold opts))
                        :dt (:dt opts)
                        :state (:state opts)}
    children))

(defn ddt-below
  "Differentiate metrics with respect to time, emits a rate-of-change event every `dt` seconds, divided by the difference in their times. if the `:metric` rate-of-change event is inferior to the threshold `threshold`, update the event state and service accordingly, and forward to children.
  Skips events without metrics.

  `opts` keys:
  - `:dt`         : emits a rate-of-change event every `dt` seconds
  - `:threshold`  : The threshold used by the above stream
  - `:state`      : The state of event forwarded to children.

  Example:
  (ddt-below {:dt 2
              :threshold 5
              :state \"critical\" }
             children)

  Set `:state` to \"critical\"  and `:service` to `ddt_`+ \"service\" of event if the derivate of metric every 2 seconds is inferior to 5"
  [opts & children]
  (apply ddt-condition {:condition-fn #(< (:metric %) (:threshold opts))
                        :dt (:dt opts)
                        :state (:state opts)}
    children))

(defn downsample
  "Generate a new event from events every `duration` seconds foreach `by` fields distinct. The new event will have `:ttl` as ttl and new-service-fn(service) as service

  `opts` keys:
  - `by`             : Map of keyword of event, generate a new event foreach fields
  - `duration`       : Duration of downsampling
  - `:ttl`           : The ttl of event forwarded to children.
  - `:new-service-fn`: How to generate the new service of event from service
  Example:
  (downsample {:by [:host :service]
               :duration 3
               :ttl 300
               :new-service-fn #(str \"received:\" (:service %) \"_from:\" (:host %))})
  Generate a new event with `:ttl` as \"received:(service of event )_from: (host of event)\" and as \"service\" every 3 seconds foreach distinct (\"service\", \"host\")
"
  [opts & children]
  (by (:by opts)
    (throttle 1 (:duration opts)
      (fn [event]
        (let [new-event (assoc event :ttl (:ttl opts) :service ((:new-service-fn opts) event))]
          (call-rescue new-event children))))))


(defn percentile-crit
  [opts & children]
  (let [child-streams (remove nil?
                        [(when-let [warning-fn (:warning-fn opts)]
                           (where (warning-fn event)
                             (with :state "warning"
                               (fn [event]
                                 (call-rescue event children)))))
                         (when-let [critical-fn (:critical-fn opts)]
                           (where (critical-fn event)
                             (with :state "critical"
                               (fn [event]
                                 (call-rescue event children)))))])]
    (where (service (str (:service opts) " " (:point opts)))
      (apply sdo child-streams))))

(defn percentiles-crit
  "Calculates percentiles and alert on it.

  `opts` keys:
  - `:service`   : Filter all events using `(service (:service opts))`
  - `:duration`  : The time period in seconds.
  - `:points`    : A map, the keys are the percentiles points.
  The value should be a map with these keys:
  - `:critical-fn` a function accepting an event and returning a boolean (optional).
  - `:warning-fn` a function accepting an event and returning a boolean (optional).
  For each point, if the event match `:warning-fn` and `:critical-fn`, the event `:state` will be \"warning\" or \"critical\"

Example:

(percentiles-crit {:service \"api req\"
                   :duration 20
                   :points {1 {:critical-fn #(> (:metric %) 100)
                               :warning-fn #(> (:metric %) 100)}
                            0.50 {:critical-fn #(> (:metric %) 500)}
                            0 {:critical-fn #(> (:metric %) 1000)}}}"
  [opts & children]
  (let [points (mapv first (:points opts))
        percentiles-streams (mapv (fn [[point conf]]
                                    (percentile-crit
                                      (assoc conf :service (:service opts)
                                                  :point point)
                                      (first children)))
                              (:points opts))
        children (conj percentiles-streams (second children))]
    (where (service (:service opts))
      (percentiles (:duration opts) points
        (fn [event]
          (call-rescue event children))))))

(defn scount
  "Takes a time period in seconds `:duration`.

  Lazily count the number of events in `:duration` seconds time windows.
  Forward the result to children

  `opts` keys:
  - `:duration`   : The time period in seconds.

  Example:

  (scount {:duration 20} children)

  Will count the number of events in 20 seconds time windows and forward the result to children."
  [opts & children]
  (fixed-time-window (:duration opts)
    (smap riemann.folds/count
      (fn [event]
        (call-rescue event children)))))

(defn scount-crit
  "Takes a time period in seconds `:duration`.

  Lazily count the number of events in `:duration` seconds time windows.
  Use the `:warning-fn` and `:critical-fn` values (which should be function
  accepting an event) to set the event `:state` accordingly

  Forward the result to children

  `opts` keys:
  - `:duration`   : The time period in seconds.
  - `:critical-fn` : A function accepting an event and returning a boolean (optional).
  - `:warning-fn`  : A function accepting an event and returning a boolean (optional).
  Example:

  (scount-crit {:duration 20 :critical-fn #(> (:metric %) 5)} children)

  Will count the number of events in 20 seconds time windows. If the count result
  is > to 5, set `:state` to \"critical\" and forward and forward the result to
  children."
  [opts & children]
  (let [child-streams (remove nil? [(when-let [critical-fn (:critical-fn opts)]
                                      (where (critical-fn event)
                                        (with :state "critical"
                                          (fn [event]
                                            (call-rescue event children)))))
                                    (when-let [warning-fn (:warning-fn opts)]
                                      (where (warning-fn event)
                                        (with :state "warning"
                                          (fn [event]
                                            (call-rescue event children)))))])]
    (scount opts
      (apply sdo child-streams))))

(defn expired-host
  [opts & children]
  (sdo
    (where (not (expired? event))
      (with {:service "host up"
             :ttl (:ttl opts)}
        (by :host
          (throttle 1 (:throttle opts)
            (index)))))
    (expired
      (where (service "host up")
        (with :description "host stopped sending events to Riemann"
          (fn [event]
            (call-rescue event children)))))))

(defn generate-stream
  [[stream-key streams-config]]
  (let [s (condp = stream-key
            :condition condition
            :condition-during condition-during
            :above above
            :above-during above-during
            :below below
            :below-during below-during
            :outside outside
            :outside-during outside-during
            :between between
            :between-during between-during
            :regex regex
            :regex-during regex-during
            :ddt-condition ddt-condition
            :ddt-above ddt-above
            :ddt-below ddt-below
            :downsample downsample
            :scount scount
            :scount-crit scount-crit
            :percentiles-crit percentiles-crit)
        streams (mapv (fn [config]
                        (let [children (:children config)
                              stream (apply (partial s
                                              (dissoc config :children :match))
                                       children)]
                          (if-let [match-clause (:where config)]
                            (where (match-clause event)
                              stream)
                            stream)))
                  streams-config)]
    (apply sdo streams)))

(defn generate-streams
  [config]
  (let [children (mapv generate-stream config)]
    (fn [event]
      (call-rescue event children))))
