# Kafka Zookeeper Server Installation

## Precondition

### 1. Install Java 8

### 2. Install Apache Ant

- Visit Apache Ant official website, download the Ant binary zip file, for example : apache-ant-1.9.14-bin.zip
- Unzip it to the folder you want to store Apache Ant, for example, C:\Program Files\Java\apache-ant-1.9.14
- Add ANT_HOME as the Windows environment variable, and point it to your Ant folder.
- Update PATH variable, append %ANT_HOME%\bin at the end, so that you can run the Ant’s command everywhere.
- Verify ant installation by running following command in Windows Command line Terminal:

    > ant -version

## Installation

### 1. Unzip the installation package to a temp the dir, say ${temp.dir}

### 2. Download latest version of apache-zookeeper and apache-kafka, and copy the downloaded files to ${temp.dir}/download directory
- Download latest stable version of apache zookeeper from https://zookeeper.apache.org/releases.html. The downloaded file should be apache-zookeeper-x.x.x-bin.tar.gz
- Download latest stable version of Kafka from https://kafka.apache.org/downloads. The downloaded file should be kafka_2.xx-x.x.x.tgz

### 3. Open file ${temp.dir}/install.properties

```
install.dir=C:/workshop/install/Kafka-Zookeeper
zookeeper.file.name=apache-zookeeper-3.5.6-bin
kafka.file.name=kafka_2.12-2.3.0

```

- Change the install.dir value to the location you want install you Kafka-Zookeeper server;
- Change the zookeeper.file.name to the real file zookeeper file name you have downloaded;
- Change the kafka.file.name to the real file kafka file name you have downloaded.

### 4. Run ant build script

Open Windows Command Terminal:
    > cd ${temp.dir}
    > ant 

### 5. Finish the Installation 

## Start Kafka-Zookeeper Server

### 1. Go to the directory you have installed the Kafka-Zookeeper Server, say ${install.dir}

### 2. Start Zookeeper

- Open a new window of Windows Command line Terminal and cd to ${install.dir}\${zookeeper.file.name}\bin, for example C:\workshop\install\Kafka-Zookeeper\apache-zookeeper-3.5.6-bin\bin
- Run following command to start Zookeeper Server:
    > zkServer

### 3. Start Kafka Server

- Open a new window of Windows Command line Terminal and cd to ${install.dir}\${kafka.file.name}, for example C:\workshop\install\Kafka-Zookeeper\kafka_2.12-2.3.0
- Run following command to start Kafka Server:
    > .\bin\windows\kafka-server-start.bat .\config\server.properties


### 2. Create Topic
- Open a new window of Windows Command line Terminal and cd to ${install.dir}\${kafka.file.name}\bin\windows, for example C:\workshop\install\Kafka-Zookeeper\kafka_2.12-2.3.0\bin\windows
- Run following command to start Kafka Server:
    > kafka-topics.bat --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic <YOUR_TOPIC_NAME>
- Run following command to list topics on Kafka Server:
    > kafka-topics.bat --list --zookeeper localhost:2181