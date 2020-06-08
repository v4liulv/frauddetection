# Apache Flink 学习笔记

## 1. Apache Flink介绍

Apache Flink是一个用于分布式流和批处理数据处理的开源平台。Flink的核心是一个流数据流引擎，为数据流上的分布式计算提供数据分发、通信和容错。Flink将批处理构建在流引擎之上，覆盖了本机迭代支持、托管内存和程序优化。

既然有了Apache Spark，为什么还要使用Apache Flink？

因为Flink是一个纯流式计算引擎，而类似于Spark这种微批的引擎，只是Flink流式引擎的一个特例。其他的不同点之后会陆续谈到。

### 1.1. 历史

Flink起源于一个叫做Stratosphere的研究项目，目标是建立下一代大数据分析引擎，其在2014年4月16日成为Apache的孵化项目，从Stratosphere 0.6开始，正式更名为Flink。Flink 0.7中介绍了最重要的特性：Streaming API。最初只支持Java API，后来增加了Scala API。

### 1.2. 架构

Flink 1.X版本的包含了各种各样的组件，包括部署、flink core（runtime）以及API和各种库。

从部署上讲，Flink支持local模式、集群模式（standalone集群或者Yarn集群）、云端部署。Runtime是主要的数据处理引擎，它以JobGraph形式的API接收程序，JobGraph是一个简单的并行数据流，包含一系列的tasks，每个task包含了输入和输出（source和sink例外）。

DataStream API和DataSet API是流处理和批处理的应用程序接口，当程序在编译时，生成JobGraph。编译完成后，根据API的不同，优化器（批或流）会生成不同的执行计划。根据部署方式的不同，优化后的JobGraph被提交给了executors去执行。

### 1.3. 分布式执行

Flink分布式程序包含2个主要的进程：JobManager和TaskManager.当程序运行时，不同的进程就会参与其中，包括Jobmanager、TaskManager和JobClient。

首先，Flink程序提交给JobClient，JobClient再提交到JobManager，JobManager负责资源的协调和Job的执行。一旦资源分配完成，task就会分配到不同的TaskManager，TaskManager会初始化线程去执行task，并根据程序的执行状态向JobManager反馈，执行的状态包括starting、in progress、finished以及canceled和failing等。当Job执行完成，结果会返回给客户端。

### 1.4. JobManager

Master进程，负责Job的管理和资源的协调。包括任务调度，检查点管理，失败恢复等。

当然，对于集群HA模式，可以同时多个master进程，其中一个作为leader，其他作为standby。当leader失败时，会选出一个standby的master作为新的leader（通过zookeeper实现leader选举）。
JobManager包含了3个重要的组件：

## 2. 学习汇总

### 2.1. 第一步

- 代码演练:按照一步步的指导，在Flink的api中实现一个简单的应用程序或查询。
  - [实现DataStream应用程序](https://ci.apache.org/projects/flink/flink-docs-stable/getting-started/walkthroughs/datastream_api.html)
  - [编写一个API查询表](https://ci.apache.org/projects/flink/flink-docs-stable/getting-started/walkthroughs/table_api.html)

- Docker游乐场:在短短几分钟内建立一个沙盒的Flink环境来探索和玩Flink
  - [运行和管理Flink流应用程序](https://ci.apache.org/projects/flink/flink-docs-stable/getting-started/docker-playgrounds/flink-operations-playground.html)

- 教程:在本地机器上安装Flink。
  - [设置一个本地Flink集群](https://ci.apache.org/projects/flink/flink-docs-stable/getting-started/tutorials/local_setup.html)

- 概念:了解Flink的基本概念，以便更好地理解文档。
  - [数据流编程模型](https://ci.apache.org/projects/flink/flink-docs-stable/concepts/programming-model.html)
  - [分布式运行时](https://ci.apache.org/projects/flink/flink-docs-stable/concepts/runtime.html)
  - [术语表](https://ci.apache.org/projects/flink/flink-docs-stable/concepts/glossary.html)

### 2.2. API 参考

API引用列表并解释了Flink API的所有特性。

- [Basic API Concepts](https://ci.apache.org/projects/flink/flink-docs-stable/dev/api_concepts.html)
- [DataStream API](https://ci.apache.org/projects/flink/flink-docs-stable/dev/datastream_api.html)
- [DataSet API](https://ci.apache.org/projects/flink/flink-docs-stable/dev/batch/index.html)
- [Table API & SQL](https://ci.apache.org/projects/flink/flink-docs-stable/dev/table/index.html)

### 2.3. 生产检查

在将Flink作业投入生产之前，请阅读[生产准备检查表](https://ci.apache.org/projects/flink/flink-docs-stable/ops/production_ready.html)。

## 3. 开始学习

### 3.1. DataStream API

Apache Flink提供了一个DataStream API，用于构建健壮的、有状态的流应用程序。它提供了对状态和时间的细粒度控制，从而允许实现高级事件驱动系统。在这个分步指导中，您将学习如何使用Flink的DataStream API构建一个有状态流应用程序。

#### 应用实例场景

在数字时代，信用卡诈骗越来越令人担忧。犯罪分子通过行骗或入侵不安全的系统来窃取信用卡号码。通过一次或多次小的购买来测试被盗号码，通常是一美元或更少。如果这可行的话，他们就会购买更重要的物品来出售或保留自己的物品。

在本教程中，您将构建一个欺诈检测系统，用于对可疑的信用卡交易发出警报。通过使用一组简单的规则，您将看到Flink如何允许我们实现高级业务逻辑和实时操作。

#### 先决条件

本演练假设您熟悉Java或Scala，但是即使您来自不同的编程语言，您也应该能够跟上。

- Java 8或11
- Maven

#### 支持

如果您遇到了困难，请查看[社区支持资源](https://flink.apache.org/gettinghelp.html)。特别是，Apache Flink的用户邮件列表一直被列为最活跃的Apache项目之一，并且是快速获得帮助的好方法。

#### 如何继续

提供的Flink Maven原型将快速创建包含所有必要依赖项的框架项目，因此您只需要专注于填充业务逻辑。这些依赖项包括Flink -stream -java，它是所有Flink流应用程序的核心依赖项;还有Flink -walkthrough-common，它具有特定于此演练的数据生成器和其他类。

**注意**:为简洁起见，本演练中的每个代码块可能不包含周围的完整类。完整的代码可以在页面的底部找到。






