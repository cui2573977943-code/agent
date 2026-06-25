package com.aifinance.service;

import com.aifinance.dto.Dtos;
import com.aifinance.dto.Views;
import com.aifinance.entity.Asset;
import com.aifinance.entity.Holding;
import com.aifinance.entity.TransactionRecord;
import com.aifinance.repository.HoldingRepository;
import com.aifinance.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 持仓、交易与盈亏计算。
 */
@Service
public class HoldingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final AssetService assetService;

    public HoldingService(HoldingRepository holdingRepository,
                          TransactionRepository transactionRepository,
                          AssetService assetService) {
        this.holdingRepository = holdingRepository;
        this.transactionRepository = transactionRepository;
        this.assetService = assetService;
    }

    /**
     * 记录一笔交易并更新持仓与盈亏。
     */
    @Transactional
    public TransactionRecord recordTransaction(Dtos.TransactionRequest req) {
        Asset asset = assetService.getById(req.getAssetId());
        String type = req.getType().toUpperCase();
        if (!type.equals("BUY") && !type.equals("SELL")) {
            throw new IllegalArgumentException("交易类型必须是 BUY 或 SELL");
        }
        BigDecimal shares = req.getShares();
        BigDecimal price = req.getPrice();
        BigDecimal fee = req.getFee() == null ? BigDecimal.ZERO : req.getFee();
        BigDecimal amount = shares.multiply(price).setScale(2, RoundingMode.HALF_UP);

        Holding holding = holdingRepository.findByAssetId(asset.getId())
                .orElseGet(() -> {
                    Holding h = new Holding();
                    h.setAssetId(asset.getId());
                    h.setShares(BigDecimal.ZERO);
                    h.setAvgCost(BigDecimal.ZERO);
                    h.setInvestedAmount(BigDecimal.ZERO);
                    h.setRealizedProfit(BigDecimal.ZERO);
                    return h;
                });

        TransactionRecord txn = new TransactionRecord();
        txn.setAssetId(asset.getId());
        txn.setType(type);
        txn.setShares(shares);
        txn.setPrice(price);
        txn.setAmount(amount);
        txn.setFee(fee);
        txn.setTradeDate(req.getTradeDate() == null ? LocalDate.now() : req.getTradeDate());
        txn.setNote(req.getNote());

        if (type.equals("BUY")) {
            BigDecimal costAdded = amount.add(fee);
            BigDecimal newShares = holding.getShares().add(shares);
            BigDecimal newInvested = holding.getInvestedAmount().add(costAdded);
            holding.setShares(newShares);
            holding.setInvestedAmount(newInvested);
            holding.setAvgCost(newShares.signum() == 0 ? BigDecimal.ZERO
                    : newInvested.divide(newShares, 4, RoundingMode.HALF_UP));
            txn.setRealizedProfit(BigDecimal.ZERO);
        } else { // SELL
            if (shares.compareTo(holding.getShares()) > 0) {
                throw new IllegalArgumentException("卖出份额(" + shares + ")超过持仓(" + holding.getShares() + ")");
            }
            BigDecimal proceeds = amount.subtract(fee);
            BigDecimal costOfSold = holding.getAvgCost().multiply(shares).setScale(2, RoundingMode.HALF_UP);
            BigDecimal realized = proceeds.subtract(costOfSold);
            txn.setRealizedProfit(realized);

            holding.setShares(holding.getShares().subtract(shares));
            holding.setInvestedAmount(holding.getInvestedAmount().subtract(costOfSold).max(BigDecimal.ZERO));
            holding.setRealizedProfit(holding.getRealizedProfit().add(realized));
            if (holding.getShares().signum() == 0) {
                holding.setAvgCost(BigDecimal.ZERO);
                holding.setInvestedAmount(BigDecimal.ZERO);
            }
        }

        holdingRepository.save(holding);
        return transactionRepository.save(txn);
    }

    public List<TransactionRecord> transactionsOf(Long assetId) {
        return transactionRepository.findByAssetIdOrderByTradeDateDescIdDesc(assetId);
    }

    public List<TransactionRecord> allTransactions() {
        return transactionRepository.findAllByOrderByTradeDateDescIdDesc();
    }

    /**
     * 列出所有持仓视图(含实时盈亏)。
     */
    public List<Views.HoldingView> listHoldings() {
        List<Views.HoldingView> views = new ArrayList<>();
        for (Holding h : holdingRepository.findAll()) {
            if (h.getShares() == null || h.getShares().signum() == 0) {
                continue;
            }
            Asset asset = assetService.getById(h.getAssetId());
            views.add(toView(h, asset));
        }
        return views;
    }

    private Views.HoldingView toView(Holding h, Asset asset) {
        Views.HoldingView v = new Views.HoldingView();
        v.setAssetId(asset.getId());
        v.setCode(asset.getCode());
        v.setName(asset.getName());
        v.setType(asset.getType());
        v.setMarket(asset.getMarket());
        v.setShares(h.getShares());
        v.setAvgCost(h.getAvgCost());
        v.setLatestPrice(asset.getLatestPrice());
        v.setChangePct(asset.getChangePct());
        v.setInvestedAmount(h.getInvestedAmount());
        v.setRealizedProfit(h.getRealizedProfit());

        BigDecimal price = asset.getLatestPrice() == null ? h.getAvgCost() : asset.getLatestPrice();
        BigDecimal marketValue = price.multiply(h.getShares()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal unrealized = marketValue.subtract(h.getInvestedAmount());
        BigDecimal total = unrealized.add(h.getRealizedProfit());
        v.setMarketValue(marketValue);
        v.setUnrealizedProfit(unrealized);
        v.setTotalProfit(total);
        if (h.getInvestedAmount().signum() != 0) {
            v.setProfitRate(total.multiply(HUNDRED)
                    .divide(h.getInvestedAmount(), 2, RoundingMode.HALF_UP));
        } else {
            v.setProfitRate(BigDecimal.ZERO);
        }
        return v;
    }

    /**
     * 组合汇总。
     */
    public Views.PortfolioSummary summary() {
        List<Views.HoldingView> holdings = listHoldings();
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal market = BigDecimal.ZERO;
        BigDecimal unrealized = BigDecimal.ZERO;
        for (Views.HoldingView v : holdings) {
            invested = invested.add(v.getInvestedAmount());
            market = market.add(v.getMarketValue());
            unrealized = unrealized.add(v.getUnrealizedProfit());
        }
        BigDecimal realized = transactionRepository.sumRealizedProfit();
        if (realized == null) realized = BigDecimal.ZERO;
        BigDecimal total = unrealized.add(realized);

        Views.PortfolioSummary s = new Views.PortfolioSummary();
        s.setTotalInvested(invested);
        s.setTotalMarketValue(market);
        s.setTotalUnrealizedProfit(unrealized);
        s.setTotalRealizedProfit(realized);
        s.setTotalProfit(total);
        s.setHoldingCount(holdings.size());
        if (invested.signum() != 0) {
            s.setTotalProfitRate(total.multiply(HUNDRED).divide(invested, 2, RoundingMode.HALF_UP));
        } else {
            s.setTotalProfitRate(BigDecimal.ZERO);
        }
        return s;
    }
}
