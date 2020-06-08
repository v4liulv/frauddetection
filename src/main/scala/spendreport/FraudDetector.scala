package spendreport

import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.scala.typeutils.Types
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import org.apache.flink.walkthrough.common.entity.Alert
import org.apache.flink.walkthrough.common.entity.Transaction

object FraudDetector {
  val SMALL_AMOUNT: Double = 1.00
  val LARGE_AMOUNT: Double = 500.00
  val ONE_MINUTE: Long     = 60 * 1000L
}

@SerialVersionUID(1L)
class FraudDetector extends KeyedProcessFunction[Long, Transaction, Alert] {

/**
 * ValueState是一个包装类，类似于Java标准库中的AtomicReference或AtomicLong互的方法;
 * update设置状态，value获取当前值，clear删除其内容。如果特定键的状态为空，例如在应用程序开始时或调用。它提供了三种与内容交
 * ValueState#clear之后，则ValueState#value将返回null。
 * 对ValueState#value返回的对象的修改不能保证被系统识别，因此所有的修改都必须使用ValueState#update执行。
 * 否则，容错是由Flink在幕后自动管理的，因此您可以像与任何标准变量交互一样与它交互。
 * */
  @transient private var flagState: ValueState[java.lang.Boolean] = _
  @transient private var timerState: ValueState[java.lang.Long] = _

  /**
   *
   * 要取消定时器，您必须记住它设置的时间，记住意味着状态，所以您将首先创建一个计时器状态和您的标志状态。
   *
   * @param parameters
   * @throws
   */
  @throws[Exception]
  override def open(parameters: Configuration): Unit = {
    val flagDescriptor = new ValueStateDescriptor("flag", Types.BOOLEAN)
    flagState = getRuntimeContext.getState(flagDescriptor)

    val timerDescriptor = new ValueStateDescriptor("timer-state", Types.LONG)
    timerState = getRuntimeContext.getState(timerDescriptor)
  }

  /**
   *
   * 下面，您将看到如何使用标志状态跟踪潜在的欺诈交易的示例。
   *
   * 骗子不会等待很长时间来完成他们的大宗购买，以减少他们的测试交易被注意的机会。例如，假设您希望将欺诈检测器设置为1分钟超时;
   * 即。，在前面的示例中，事务3和事务4只有在1分钟内发生时才被认为是欺诈。Flink的KeyedProcessFunction允许你设置计时器，
   * 在将来的某个时间点调用回调方法。
   *
   * 让我们看看如何修改我们的工作，以符合我们的新要求:
   * -当标志被设置为true时，也设置一个1分钟的计时器。
   * -当计时器触发时，通过清除其状态重置标志。
   * -如果该标志被清除，则应该取消计时器。
   *
   * @param transaction
   * @param context
   * @param collector
   */
  override def processElement(
                               transaction: Transaction,
                               context: KeyedProcessFunction[Long, Transaction, Alert]#Context,
                               collector: Collector[Alert]): Unit = {

    // Get the current state for the current key
    val lastTransactionWasSmall = flagState.value

    println(transaction.getAmount)
    // Check if the flag is set
    if (lastTransactionWasSmall != null) {
      if (transaction.getAmount > FraudDetector.LARGE_AMOUNT) {
        // Output an alert downstream
        val alert = new Alert
        alert.setId(transaction.getAccountId)
        collector.collect(alert)
      }
      // Clean up our state
      cleanUp(context)
    }

    /**
     * KeyedProcessFunction#processElement是通过包含计时器服务的上下文调用的。计时器服务可用于查询当前时间、注册计时器
     * 和删除计时器。这样，您就可以在每次设置标志时设置一个1分钟的计时器，并将时间戳存储在timerState中。
     *
     * context.timerService: 用于查询时间和注册计时器
     */
    if (transaction.getAmount < FraudDetector.SMALL_AMOUNT) {
      // set the flag to true
      flagState.update(true)

      //设置当前处理时间 + 1分钟
      //设置计时器和计时器状态
      val timer = context.timerService.currentProcessingTime + FraudDetector.ONE_MINUTE
      //注册一个计时器，当处理时间超过给定时间时触发。
      context.timerService.registerProcessingTimeTimer(timer)
      timerState.update(timer)
    }
  }

  /**
   * 加工时间为挂钟时间，由操作者运行机器的系统时钟决定。
   * 当计时器触发时，它调用KeyedProcessFunction#onTimer。重写此方法是实现回调来重置标志的方法。
   *
   * @param timestamp
   * @param ctx
   * @param out
   */
  override def onTimer(
                        timestamp: Long,
                        ctx: KeyedProcessFunction[Long, Transaction, Alert]#OnTimerContext,
                        out: Collector[Alert]): Unit = {
    // remove flag after 1 minute
    timerState.clear()
    flagState.clear()
  }

  /**
   * 最后，要取消计时器，需要删除已注册的计时器和计时器状态。您可以将其封装在一个helper方法中并调用该方法，
   * 而不是flag .clear()。
   *
   * @param ctx
   * @throws
   */
  @throws[Exception]
  private def cleanUp(ctx: KeyedProcessFunction[Long, Transaction, Alert]#Context): Unit = {
    // delete timer
    val timer = timerState.value
    ctx.timerService.deleteProcessingTimeTimer(timer)

    // clean up all states
    timerState.clear()
    flagState.clear()
  }
}