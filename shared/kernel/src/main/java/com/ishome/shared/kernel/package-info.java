/**
 * 共享领域原语（ULID、统一错误信封等，随首个真实用例入驻，保持薄）。
 *
 * <p>纪律：本包不得依赖 Spring / MyBatis / 任何 Web 框架——它被所有服务的 domain 层引用， 引入技术细节等于污染全部服务的领域层。
 */
package com.ishome.shared.kernel;
