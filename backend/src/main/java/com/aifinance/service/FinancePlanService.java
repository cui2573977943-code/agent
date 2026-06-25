package com.aifinance.service;

import com.aifinance.dto.Dtos;
import com.aifinance.entity.*;
import com.aifinance.repository.*;
import com.aifinance.service.ai.tot.FinancePlanAgent;
import com.aifinance.service.ai.tot.FinancePlanOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 理财规划编排: 组装历史盈亏/持仓/可选标的 -> 调用思维树 Agent -> 落库。
 */
@Service
public class FinancePlanService {

    private static final Logger log = LoggerFactory.getLogger(FinancePlanService.class);

    private final AiConfigService aiConfigService;
    private final FinancePlanAgent financePlanAgent;
    private final FinancePlanRepository financePlanRepository;
    private final SalaryRepository salaryRepository;
    private final AssetRepository assetRepository;
    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    public FinancePlanService(AiConfigService aiConfigService, FinancePlanAgent financePlanAgent,
                              FinancePlanRepository financePlanRepository, SalaryRepository salaryRepository,
                              AssetRepository assetRepository, HoldingRepository holdingRepository,
                              TransactionRepository transactionRepository, ObjectMapper objectMapper) {
        this.aiConfigService = aiConfigService;
        this.financePlanAgent = financePlanAgent;
        this.financePlanRepository = financePlanRepository;
        this.salaryRepository = salaryRepository;
        this.assetRepository = assetRepository;
        this.holdingRepository = holdingRepository;
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
    }

    public FinancePlanOutcome plan(BigDecimal salary, BigDecimal expense) {
        AiConfig config = aiConfigService.getActiveConfigOrThrow();
        double s = salary.doubleValue();
        double e = expense == null ? 0 : expense.doubleValue();

        String historyPnl = buildHistoryPnl();
        String holdings = buildHoldingsSummary();
        String candidates = buildCandidateAssets();

        FinancePlanOutcome outcome = financePlanAgent.run(config, s, e, historyPnl, holdings, candidates);
        save(salary, expense, outcome);
        return outcome;
    }

    private void save(BigDecimal salary, BigDecimal expense, FinancePlanOutcome outcome) {
        try {
            FinancePlan fp = new FinancePlan();
            fp.setMonthlySalary(salary);
            fp.setMonthlyExpense(expense == null ? BigDecimal.ZERO : expense);
            fp.setRiskStyle(outcome.getRiskStyle());
            fp.setEmergencyFund(BigDecimal.valueOf(outcome.getEmergencyFund()));
            fp.setInvestableAmount(BigDecimal.valueOf(outcome.getInvestableAmount()));
            fp.setConfidence(BigDecimal.valueOf(outcome.getConfidence()));
            fp.setRounds(outcome.getRounds());
            fp.setDecomposition(String.join(" | ", outcome.getDecomposition()));
            fp.setThoughtTree(objectMapper.writeValueAsString(
                    financePlanAgent.treeToMap(outcome.getThoughtTree())));
            fp.setPlanDetail(objectMapper.writeValueAsString(outcome.getAllocations()));
            fp.setReasoning(outcome.getReasoning());
            financePlanRepository.save(fp);
        } catch (Exception ex) {
            log.warn("理财规划落库失败: {}", ex.getMessage());
        }
    }

    public List<FinancePlan> history() {
        return financePlanRepository.findTop50ByOrderByCreatedAtDesc();
    }

    // ---------------- 工资记录 ----------------

    public SalaryRecord saveSalary(Dtos.SalaryRequest req) {
        SalaryRecord r = salaryRepository.findAllByOrderByMonthDesc().stream()
                .filter(x -> x.getMonth().equals(req.getMonth()))
                .findFirst().orElse(new SalaryRecord());
        r.setMonth(req.getMonth());
        r.setSalary(req.getSalary());
        r.setExpense(req.getExpense() == null ? BigDecimal.ZERO : req.getExpense());
        r.setSaving(r.getSalary().subtract(r.getExpense()));
        r.setNote(req.getNote());
        return salaryRepository.save(r);
    }

    public List<SalaryRecord> salaryHistory() {
        return salaryRepository.findAllByOrderByMonthDesc();
    }

    // ---------------- 上下文组装 ----------------

    private String buildHistoryPnl() {
        BigDecimal realized = transactionRepository.sumRealizedProfit();
        if (realized == null) realized = BigDecimal.ZERO;
        StringBuilder sb = new StringBuilder();
        sb.append("累计已实现盈亏: ").append(realized).append(" 元。");
        if (realized.signum() > 0) {
            sb.append("(历史整体盈利, 可适度提升风险偏好)");
        } else if (realized.signum() < 0) {
            sb.append("(历史整体亏损, 建议更保守)");
        } else {
            sb.append("(暂无明显盈亏记录)");
        }

        List<TransactionRecord> txns = transactionRepository.findAllByOrderByTradeDateDescIdDesc();
        if (!txns.isEmpty()) {
            sb.append("\n近期交易: ");
            sb.append(txns.stream().limit(15)
                    .map(t -> t.getTradeDate() + " " + t.getType()
                            + (t.getRealizedProfit().signum() != 0
                            ? "(盈亏" + t.getRealizedProfit() + ")" : ""))
                    .collect(Collectors.joining("; ")));
        }
        return sb.toString();
    }

    private String buildHoldingsSummary() {
        List<Holding> holdings = holdingRepository.findAll().stream()
                .filter(h -> h.getShares() != null && h.getShares().signum() != 0)
                .toList();
        if (holdings.isEmpty()) {
            return "当前无持仓。";
        }
        return holdings.stream().map(h -> {
            String name = assetRepository.findById(h.getAssetId())
                    .map(Asset::getName).orElse("资产#" + h.getAssetId());
            return name + ": 份额" + h.getShares() + ", 成本" + h.getInvestedAmount()
                    + ", 已实现盈亏" + h.getRealizedProfit();
        }).collect(Collectors.joining("\n"));
    }

    private String buildCandidateAssets() {
        List<Asset> assets = assetRepository.findAll();
        if (assets.isEmpty()) {
            return "(暂无候选标的, 可建议宽基指数基金/货币基金/债券基金等通用品种)";
        }
        return assets.stream()
                .map(a -> a.getName() + "(" + a.getCode() + "," + a.getType() + ")"
                        + (a.getChangePct() != null ? " 当日" + a.getChangePct() + "%" : ""))
                .collect(Collectors.joining("; "));
    }
}
