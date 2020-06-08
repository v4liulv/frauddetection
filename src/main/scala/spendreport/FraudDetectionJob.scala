/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spendreport

import org.apache.flink.streaming.api.scala._
import org.apache.flink.walkthrough.common.sink.AlertSink
import org.apache.flink.walkthrough.common.entity.Alert
import org.apache.flink.walkthrough.common.entity.Transaction
import org.apache.flink.walkthrough.common.source.TransactionSource

/**
  * Skeleton code for the DataStream code walkthrough
 *
 * FraudDetectionJob类定义应用程序的数据流
 *
 *
  */
object FraudDetectionJob {

  @throws[Exception]
  def main(args: Array[String]): Unit = {
    //设置了StreamExecutionEnvironment。执行环境是为作业设置属性、创建源并最终触发作业执行的方式。
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    //从外部系统(如Apache Kafka、Rabbit MQ或Apache Pulsar)摄取数据到Flink作业。本演练使用一个源，该源生成要
    // 处理的无限信用卡交易流。每个事务包含一个帐户ID (accountId)、事务发生时的时间戳(timestamp)和美元金额
    // (amount)。附加到源文件上的名称只是为了调试的目的，所以如果出现错误，我们将知道错误的来源。
    val transactions: DataStream[Transaction] = env
      .addSource(new TransactionSource)
      .name("transactions")

    /**
     * 事务流包含来自大量用户的大量事务，因此需要并行处理多个欺诈检测任务。由于欺诈发生在每个帐户上，因此必须确保相同帐
     * 户的所有交易都由欺诈检测器操作符的相同并行任务处理。
     *
     * 为了确保相同的物理任务处理特定键的所有记录，可以使用DataStream#keyBy对流进行分区。process()调用添加一个运算符，
     * 该运算符将函数应用于流中的每个分区元素。通常说，紧接在keyBy之后的操作符(在本例中为欺诈检测器)是在一个键控上下文中
     * 执行的。
     */
    val alerts: DataStream[Alert] = transactions
      .keyBy(transaction => transaction.getAccountId)
      .process(new FraudDetector)
      .name("fraud-detector")

    /**
     * 接收将数据令写入外部系统;比如Apache Kafka、Cassandra和AWS Kinesis。AlertSink用日志级别的信息记录每个警告记录，
     * 而不是将其写入持久存储，因此您可以很容易地看到结果。
     */
    alerts
      .addSink(new AlertSink)
      .name("send-alerts")

    /**
     * Flink应用程序是惰性地构建的，并且只在完全形成后才被交付给集群执行。调用StreamExecutionEnvironment#execute来开始
     * 我们的作业的执行，并给它一个名称。
     */
    env.execute("Fraud Detection")
  }
}
