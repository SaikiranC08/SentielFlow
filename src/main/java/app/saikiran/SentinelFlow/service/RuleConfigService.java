package app.saikiran.SentinelFlow.service;

import app.saikiran.SentinelFlow.model.entity.RuleConfig;
import app.saikiran.SentinelFlow.model.enums.AccountType;
import app.saikiran.SentinelFlow.model.enums.RuleName;
import app.saikiran.SentinelFlow.model.enums.RuleScope;
import app.saikiran.SentinelFlow.repository.RuleConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleConfigService {

    private final RuleConfigRepository ruleConfigRepository;

    public double getMultiplierThreshold(RuleName ruleName, String accountId, AccountType accountType) {
        List<RuleConfig> configs = ruleConfigRepository.findByRuleName(ruleName);

        // 1. Check per-account override
        if (accountId != null) {
            Optional<RuleConfig> accountOverride = configs.stream()
                    .filter(c -> c.getScope() == RuleScope.ACCOUNT && accountId.equals(c.getScopeValue()))
                    .findFirst();
            if (accountOverride.isPresent() && accountOverride.get().getMultiplierThreshold() != null) {
                return accountOverride.get().getMultiplierThreshold();
            }
        }

        // 2. Check account-type default
        if (accountType != null) {
            Optional<RuleConfig> typeDefault = configs.stream()
                    .filter(c -> c.getScope() == RuleScope.ACCOUNT_TYPE && accountType.name().equals(c.getScopeValue()))
                    .findFirst();
            if (typeDefault.isPresent() && typeDefault.get().getMultiplierThreshold() != null) {
                return typeDefault.get().getMultiplierThreshold();
            }
            // Fallback to enum built-in threshold if database entry missing
            return accountType.getDefaultMultiplierThreshold();
        }

        // 3. Check global default
        Optional<RuleConfig> globalDefault = configs.stream()
                .filter(c -> c.getScope() == RuleScope.GLOBAL)
                .findFirst();
        if (globalDefault.isPresent() && globalDefault.get().getMultiplierThreshold() != null) {
            return globalDefault.get().getMultiplierThreshold();
        }

        return 3.0; // Ultimate fallback
    }

    public int getWindowMinutes(RuleName ruleName) {
        List<RuleConfig> configs = ruleConfigRepository.findByRuleName(ruleName);
        return configs.stream()
                .filter(c -> c.getWindowMinutes() != null)
                .map(RuleConfig::getWindowMinutes)
                .findFirst()
                .orElse(10);
    }
}
