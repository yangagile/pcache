**PCache (Parallel Cache) for Object Storage** - A simple, high-performance, low-cost parallel caching system focused on improving object storage performance. Released under the Apache 2.0 open-source license. PCache abstracts away differences between backend object storage vendors, enabling easy improvement of data throughput for big data, machine learning, artificial intelligence, and various application platforms. -> [To Chinese](README_CN.md)

## Architecture
Files cached across multiple cache nodes (PCPs), large files are split into blocks. Clients read from/write to files in parallel from multiple PCPs. If a file is not in the cache, it triggers multiple PCPs to pull data in parallel from the backend object storage. Different files are stored on different cloud vendors or regions according to routing rules, but are all cached on the local distributed PCP nodes.

<div align="center">
<img src="docs/images/pc-arch.png" alt="PCache Architecture" width="60%" />
</div>

## Highlighted Features

### 1. Parallel Cache based on Object Storage (PCache)
* PCache is designed based on object storage fundamentals, with a simple logic that focuses solely on high performance. Reliability and scalability are ensured by the backend object storage.
* PCache can directly accelerate existing object storage data without the need for metadata conversion.
* Large files split into blocks, and small files are distributed and cached across multiple PCP nodes, providing high-bandwidth and high-QPS storage services.
* Cache nodes (PCP) are managed through consistent hashing. Clients access PCP nodes directly.

### 2. Parallel Bucket (PBucket)
* Supports multi-cloud. A PBucket corresponds to one or a group of cloud provider buckets. Verified with (AWS S3, Alibaba OSS, Baidu BOS, Volcano TOS, Minio).
* In high-concurrency scenarios, a PBucket corresponds to a group of provider buckets; In high-reliability scenarios, a PBucket corresponds to multi-region, multi-provider buckets.
* Supports defining lifecycle policies (TTL) to specify the retention time for data in different storage tiers (hot/cold/archive).
* Supports AK/SK/IAM, independent backend object storage, with resource/interface permissions authorized through policies and roles.
<div align="center">
<img src="docs/images/pc-router.png" alt="PCache Architecture" width="60%" />
</div>


### 3. Command Line Support
Provides the pcmd command-line tool, implemented by invoking the Go SDK, for accessing data via commands (put/get/sync). The sync command can synchronize data between a local directory and a PBucket prefix based on multiple strategies (presence/size/fingerprint).
```
// sync local folder '/tmp/meta' to pbucket 'test-minio' prefix 'test/pcom/sync/meta'
$ ./pcmd sync /tmp/meta s3://test-minio/test/pcom/sync/meta

// sync pbucket 'test-minio' prefix 'test/pcom/sync/meta' to local folder '/tmp/meta'
$ ./pcmd sync s3://test-minio/test/pcom/sync/meta /tmp/meta

$ ./pcmd sync -h
Usage of sync:
  -block-thead-number int
    	thread number of block worker (default 8)
  -checksum string
    	checksum file for verify or compare, crc32 or md5
  -debug
    	debug mode
  -dry
    	dry run mode
  -http-timeout-factor float
    	block http timeout factor (default 1)
  -skip-existing
    	skip existing file or object
  -skip-unchanged
    	skip unchanged file or object with size for checksum  	
```
### 4. SDK Support
PBucket can be accessed via SDK, with Java/Go languages supported.

## User Story
### 1.AI Training and Inference Platform
Unified management of multiple data types using object storage, with PCache providing on-demand acceleration across various scenarios.
* During training, training datasets are loaded via PCache with support for preheating. Generated models saved to PCache can be directly used for inference and are eventually synchronized back to object storage.
* During inference, models are loaded from PCache. If a model is not available, it is automatically loaded from the backend object storage.
* In the prefill phase of inference, a large number of tokens are saved to PCache, and during the decode phase, tokens are loaded directly from PCache. Alternatively, token persistence to object storage can be enabled as needed.
<div align="center">
<img src="docs/images/pc-user1.png" alt="PCache User" width="50%" />
</div>

### 2.Intelligent Driving Platform
Massive numbers of connected vehicles are distributed across various regions, with devices uploading data locally to public cloud object storage. Users access this data via mobile phones or computers. AI training platforms, big data systems, and business services deployed in central high-speed networks require batch and high-speed access to vehicle-uploaded data stored across different object storage locations.

PCache provides a unified data access interface for various services across data centers, masking regional differences among cloud providers and delivering high-speed storage services.
<div align="center">
<img src="docs/images/pc-user.png" alt="PCache User" width="80%" />
</div>

## Modules
PCache consists of four modules:

### Parallel Meta Service (PMS)
* Manages Meta such as cloud vendors and PBuckets. For details, refer to [Meta Storage Solution](docs/en/meta.md)。

* Provides STS authentication services for multiple cloud vendors, granting clients access permissions to object storage.

* Manages cache nodes (PCPs) and provides clients with a list of available PCPs.

* Supports Multi-PMS deployment.[How to use multiple PMS](docs/en/multi-pms.md).

### Parallel Cache Node (PCP)
* A file block server implemented using Netty, focused on high-speed upload/download of file blocks.

* Implements cache eviction algorithms. Periodically scans through cached data blocks in the background to delete cold data.

* Registers with the PMS upon startup for availability.

### Client SDK
* Retrieves STS credentials and the list of available PCPs from the nearby PMS node.

* Performs concurrent read/write operations on data chunks from/to PCPs and assembles files.

### Command-line tool pcmd
* Calls the SDK interface to complete the file/directory upload and download functionality. For example, you can synchronize data under a prefix of a PBucket to a local directory in parallel using the following command. For more usage, refer to:  [pcmd usage](pcmd/README.md)。
    ```
    ./pcmd sync s3://pbucket-name/prefix/ /tmp/folder 
    ```

## Data Security
An independent security authentication system is employed. Both APIs and PBuckets are accessed via AK/SK and authorized through IAM. For details, refer to [Configure Interface Authentication (Token) Guide](docs/en/api_token.md).

## Validation Test
Simple Small File Test

**Test Method**：Access Baidu Object Storage (BOS) via public network to batch synchronize 10,000 small files, each 2 KB in size.

**Test Machines**： 
* Machine A: System (Ubuntu), Memory (16G), deployed with PMS/PCP
* Machine B: System (MacOS), Memory (16G), deployed with PCP/PCMD

| Operation|Time Taken (seconds)| Cache Hit Rate |S3cmd Reference |
|-----------|----------------|----------|----------|
| First Upload | 204 	   |  0% | 624 |
| First Download | 150		|  0% | 584  |
| Second Download | 7     	|  100% | 581 |

**Test Conclusion：**
*   With 2 PCP nodes and a 100% cache hit rate, the second download is 20 times faster than the first download.
*   With 2 PCP nodes, even the first upload/download is more than three times as fast compared to directly using S3cmd.

**Commands Used:**
  ```
  pcmd sync /tmp/small_2k_10k s3://pb-bos/test/pcmd/sync/small_2k_10k/
  pcmd sync s3://pb-bos/test/pcmd/sync/small_2k_10k/ /tmp/small_2k_10k/
  ```
## Getting Started
Please refer to the [Quick Start Guide](docs/en/startup.md) to start using PCache immediately!

## Contribution
Please refer to [Contributing Code](docs/en/contribute.md). Thank you for your contributions to the PCache community!!

## Milestone
| Version | Features 							                        | Status |
|---------|-----------------------------------------|--------|
| 0.1.0   | Basic Functions｜Deployment              | Done   |
| 0.2.0   | Bug fixes｜Command-line tool(pcmd)       | Done   |
| 0.3.0   | Asynchronous write optimization | In progress |
| 0.4.0   | Meta cache｜RDMA support                 | Plan |

## Maintainers
agile.yang@gmail.com

28581556@qq.com



