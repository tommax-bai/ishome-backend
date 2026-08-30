package com.ishome.project.infrastructure.client;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.ishome.project.domain.port.ReportBookStore;
import com.ishome.project.domain.rulebook.ReportBookKey;
import com.ishome.project.domain.rulebook.ReportBookLink;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 私有桶适配：判在不在 + 签一条限时链接（用户裁决 2026-08-30 晚）。
 *
 * <p>**为什么是对象存储而不是自家服务器出一个地址**：册要在业主手机上打开，而部署那台机器上跑着 别的生产服务——多开一个公网面就是多一份风险。私有桶的签名链接由 OSS
 * 域名直接对外、自带有效期， 本项目一个公网端口都不用开。同架构方案"图/视频走 CDN→OSS 直出不过网关"。
 *
 * <p>**先判在不在再签**：签名是纯本地计算，对着一个不存在的对象照样签得出来一条形态完好的链接—— 业主点开是一页 404。宁可回"还没出册"，不发一条指向空气的地址。
 *
 * <p>凭证从配置来，代码里不留任何默认桶名或端点；缺配置时本服务照常起（报告链接这一条路不通而已， 里程碑与求值线不受影响），取链接时响亮失败。
 */
@Component
public class OssReportBookStore implements ReportBookStore {

  private final OSS client;
  private final String bucket;

  public OssReportBookStore(
      @Value("${ishome.oss.endpoint:}") String endpoint,
      @Value("${ishome.oss.bucket-private:}") String bucket,
      @Value("${ishome.oss.access-key-id:}") String accessKeyId,
      @Value("${ishome.oss.access-key-secret:}") String accessKeySecret) {
    this.bucket = bucket;
    this.client =
        endpoint.isBlank() || bucket.isBlank() || accessKeyId.isBlank() || accessKeySecret.isBlank()
            ? null
            : new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
  }

  @Override
  public Optional<ReportBookLink> issueLink(String reportId, Duration validity) {
    if (client == null) {
      throw new IllegalStateException(
          "私有对象存储没配全（ishome.oss.*）——凭证放 ~/.ishome/oss-local.env（本机）"
              + "或 /opt/ishome/env/oss.env（服务器），不入库");
    }
    String key = ReportBookKey.of(reportId);
    try {
      if (!client.doesObjectExist(bucket, key)) {
        return Optional.empty();
      }
      Instant expiresAt = Instant.now().plus(validity);
      URL url = client.generatePresignedUrl(bucket, key, Date.from(expiresAt));
      return Optional.of(new ReportBookLink(URI.create(url.toString()), expiresAt));
    } catch (OSSException | com.aliyun.oss.ClientException e) {
      // 桶连不上/凭证不对与"还没出册"是两件事，不许混成同一个空 Optional：
      // 前者要人去修配置，后者只要再等一会儿。
      throw new IllegalStateException(
          "取册链接失败（桶 %s，键 %s）：%s".formatted(bucket, key, e.getMessage()), e);
    }
  }

  @PreDestroy
  void shutdown() {
    if (client != null) {
      client.shutdown();
    }
  }
}
