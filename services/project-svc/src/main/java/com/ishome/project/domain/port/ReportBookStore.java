package com.ishome.project.domain.port;

import com.ishome.project.domain.rulebook.ReportBookLink;
import java.time.Duration;
import java.util.Optional;

/**
 * 报告册的取用 port：把一份**已经出册**的报告变成一条能打开的链接。
 *
 * <p>本接口只读不写。册由渲染层写进私有桶（生成侧只写不签），业务侧只做两件事：**这份出册了没有**、
 * **给我一条能打开的地址**。两件事合成一次调用，因为它们的答案来自同一次询问——分成两次会多一趟往返， 也会让"刚问完就过期"这种缝隙冒出来。
 *
 * <p>返回空 = **这份报告还没出册**，不是错误：报告是异步生成的，问早了本来就该是"还没有"。 出册失败与还没出册在这里分不出来，也不该在这里分——那是编排侧结论要回答的问题。
 */
public interface ReportBookStore {

  Optional<ReportBookLink> issueLink(String reportId, Duration validity);
}
