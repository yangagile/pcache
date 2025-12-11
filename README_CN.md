PCache(Parallel Cache) 一款简单/高性能/低成本的并行缓存系统，专注于提高对象存储性能。在 Apache 2.0 开源协议下发布。PCache 屏蔽后端对象存储厂商差异。可以便捷的提高大数据、机器学习、人工智能以及各种应用平台数据吞吐。

## 技术架构
文件分块缓存在多个缓存节点（PCP）上，客户端从多个 PCP 并行读取/写入文件。如文件不在缓存，触发多个 PCP 节点并行从后端对象存储上拉数据。不同的文件按路由规则存储在不同的云厂商或地域上，但都会缓存在本地分布式 PCP 节点上。
<div align="center">
<img src="docs/images/pc-arch.png" alt="PCache Architecture" width="80%" />
</div>

## 核心特性
**并行缓存（PCache）**：只关心高性能，可靠性由后端对象存储保障。
   * 文件分块缓存到多个 PCP 节点，并行读取写入。PCP 节点只关注数据块下载/上传速度。
   * 元数据服务（PMS）通过一致性哈希管理通过 PCP 节点。客户端直接访问 PCP 节点。
     
**并行桶（PBucket）**：虚拟并行桶，后端对应一个或一批云厂商桶，可跨云跨区域。
  * 支持定义数据路由，数据可路由到某云某区域。 
  * 支持定义生命周期（TTL），定义数据在不同类型存储（热/冷/归档）保存时间。
  * 支持 AK/SK/IAM，独立后端对象存储，通过设置策略和角色授权资源/接口权限。
    
**多云支持**：支持基础 S3 接口的云厂商都可以接入，已验证有（AWS-S3，阿里-OSS, 百度-BOS，火山-TOS, Minio）

**SDK 支持**：可通过 SDK 访问 PBucket，已支持的语言有 Java/Go。
<div align="center">
<img src="docs/images/pc-router.png" alt="PCache Architecture" width="60%" />
</div>

## 典型场景
海量智能设备分布在各地，设备就近上传到公有云对象存储。用户通过手机或电脑访问数据。AI练平台/大数据/业务服务部署在中心高速网络，需要批量高速读取保存各处对象存储上的数据。
<div align="center">
<img src="docs/images/pc-user.png" alt="PCache User" width="80%" />
</div>

## 主要模块
PCache 由四个部分组成：
### 并行元数据服务（PMS）
   * 管理云厂商/PBucket 等信息，详见[Meta存储方案](docs/cn/meta.md)。
   * 提供多种云厂商 STS 认证服务，提供客户端访问对象存储权限。
   * 管理缓存节点（PCP），提供客户端可用缓存节点列表。
   * 可多点部署 PMS。[部署多个 PMS ](docs/cn/multi-pms.md).
     
### 并行缓存节点（PCP）
   * 使用 netty 实现的文件快服务器，专注于文件块上传/下载速度。
   * 缓存淘汰算法。后台定时便利缓存数据块，删除冷数据。
   * 启动后注册到 PMS 备用。
     
### 客户端 SDK
   * 从最近 PMS 节点获取 STS 权限，可用 PCP 列表。
   * 从 PCP 并发读/写数据块，合成文件。

### 命令行工具 pcmd
* 调用 SDK 接口完成文件/目录的上传下载功能。例如，可通过以下命令并行同步 PBucket 的前缀下数据到本地目录，更多用法参见 [pcmd 使用方式](pcmd/README.md)。
    ```
    ./pcmd sync s3://pbucket-name/prefix/ /tmp/folder 
    ```
<div align="center">
<img src="docs/images/pc-deploy.png" alt="PCache Deploy" width="80%" />
</div>

## 数据安全
独立的安全认证体系，接口和 PBuckt 都通过 AK/SK 访问，通过 IAM 授权。详见[配置接口认证（Token）指南](docs/cn/api_token.md)

## 开始使用
请参照 [快速上手指南](docs/cn/startup.md) 立即开始使用 PCache！

## 代码提交
请参照 [贡献代码](docs/cn/contribute.md) 感谢你对 PCache 社区的贡献！！

## 开发计划

|   版本   | 功能 								  |
|---------|---------------------------------------|
| 0.2.0   | 完善现有各项功能，bugfix 				  |
| 0.3.0   | 支持纠删码写缓存						  |
| 0.4.0   | SDK Python 支持     					  |
| 0.5.0   | 支持 Posix 协议读写数据。      		  |

## 维护人员
28581556@qq.com

agile.yang@gmail.com

