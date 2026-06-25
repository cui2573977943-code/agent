package com.aifinance.service;

import com.aifinance.dto.Dtos;
import com.aifinance.entity.Asset;
import com.aifinance.repository.AssetRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资产(股票/基金)管理。
 */
@Service
public class AssetService {

    private final AssetRepository assetRepository;

    public AssetService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public List<Asset> list() {
        return assetRepository.findAll();
    }

    public Asset getById(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("资产不存在: " + id));
    }

    public Asset getByCode(String code) {
        return assetRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("资产不存在: " + code));
    }

    public Asset create(Dtos.AssetRequest req) {
        if (assetRepository.existsByCode(req.getCode())) {
            throw new IllegalArgumentException("资产代码已存在: " + req.getCode());
        }
        String type = req.getType().toUpperCase();
        if (!type.equals("STOCK") && !type.equals("FUND")) {
            throw new IllegalArgumentException("类型必须是 STOCK 或 FUND");
        }
        Asset asset = new Asset();
        asset.setCode(req.getCode().trim());
        asset.setName(req.getName().trim());
        asset.setType(type);
        asset.setMarket(req.getMarket());
        asset.setLatestPrice(req.getLatestPrice());
        asset.setPrevClose(req.getPrevClose());
        if (req.getLatestPrice() != null && req.getPrevClose() != null
                && req.getPrevClose().signum() != 0) {
            asset.setChangePct(req.getLatestPrice().subtract(req.getPrevClose())
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .divide(req.getPrevClose(), 4, java.math.RoundingMode.HALF_UP));
        }
        asset.setPriceUpdatedAt(LocalDateTime.now());
        return assetRepository.save(asset);
    }

    public void delete(Long id) {
        if (!assetRepository.existsById(id)) {
            throw new IllegalArgumentException("资产不存在: " + id);
        }
        assetRepository.deleteById(id);
    }

    public Asset updatePrice(Long id, java.math.BigDecimal latestPrice) {
        Asset asset = getById(id);
        if (asset.getLatestPrice() != null) {
            asset.setPrevClose(asset.getLatestPrice());
        }
        asset.setLatestPrice(latestPrice);
        if (asset.getPrevClose() != null && asset.getPrevClose().signum() != 0) {
            asset.setChangePct(latestPrice.subtract(asset.getPrevClose())
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .divide(asset.getPrevClose(), 4, java.math.RoundingMode.HALF_UP));
        }
        asset.setPriceUpdatedAt(LocalDateTime.now());
        return assetRepository.save(asset);
    }
}
