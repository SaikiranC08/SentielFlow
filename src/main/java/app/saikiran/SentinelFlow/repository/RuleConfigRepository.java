package app.saikiran.SentinelFlow.repository;

import app.saikiran.SentinelFlow.model.entity.RuleConfig;
import app.saikiran.SentinelFlow.model.enums.RuleName;
import app.saikiran.SentinelFlow.model.enums.RuleScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RuleConfigRepository extends JpaRepository<RuleConfig, Long> {

    List<RuleConfig> findByRuleName(RuleName ruleName);

    Optional<RuleConfig> findByRuleNameAndScopeAndScopeValue(RuleName ruleName, RuleScope scope, String scopeValue);
}
