package com.exileeconomics.price.scheduler;

import com.exileeconomics.definitions.ItemDefinitionEnum;
import com.exileeconomics.entity.CurrencyRatioEntity;
import com.exileeconomics.entity.ItemDefinitionEntity;
import com.exileeconomics.entity.ItemEntity;
import com.exileeconomics.price.event.CurrencyRatioUpdateEvent;
import com.exileeconomics.price.exception.CurrencyRatioException;
import com.exileeconomics.service.CurrencyRatioService;
import com.exileeconomics.service.ItemDefinitionsService;
import com.exileeconomics.service.ItemEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

@Component
public class CurrencyRatioEventScheduler {
    private final ApplicationEventPublisher applicationEventPublisher;
    private final CurrencyRatioService currencyRatioService;
    private final ItemEntityService itemEntityService;
    private final ItemDefinitionsService itemDefinitionsService;
    private final Map<ItemDefinitionEnum, CurrencyRatioEntity> currencyRatioMap = new HashMap<>();

    /**
     * I noticed that 40 items is generally the sweet spot for a correct average price
     * <p>
     * the problem is that this can get HEAVILY skewed if there aren't enough items on the market
     */
    private final int IMPOSED_LIMIT = 40;

    public CurrencyRatioEventScheduler(
            @Autowired ApplicationEventPublisher applicationEventPublisher,
            @Autowired CurrencyRatioService currencyRatioService,
            @Autowired ItemEntityService itemEntityService,
            @Autowired ItemDefinitionsService itemDefinitionsService
    ) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.currencyRatioService = currencyRatioService;
        this.itemEntityService = itemEntityService;
        this.itemDefinitionsService = itemDefinitionsService;
    }

    public void publishCurrencyRatioRecalculatedEvent(Map<ItemDefinitionEnum, CurrencyRatioEntity> currencyRatioMap) {
        CurrencyRatioUpdateEvent customSpringEvent = new CurrencyRatioUpdateEvent(this, currencyRatioMap);
        applicationEventPublisher.publishEvent(customSpringEvent);
    }

    @Transactional(rollbackFor = {RuntimeException.class, Error.class, CurrencyRatioException.class})
    @Scheduled(cron = "0 0 23 * * *")
    public void scheduledCurrencyRatioUpdateBasedOnAveragePriceOfItemEntriesEvery12Hours() throws CurrencyRatioException {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now.getTime());

        cal.add(Calendar.HOUR, -24);
        Timestamp nowMinus12Hours = new Timestamp(cal.getTime().getTime());

        ItemDefinitionEntity chaosOrbEntity = itemDefinitionsService.findFirsItemDefinitionByItemDefinitionEnum(ItemDefinitionEnum.CHAOS_ORB);

        // divine average price calc
        ItemDefinitionEntity divineOrbEntity = itemDefinitionsService.findFirsItemDefinitionByItemDefinitionEnum(ItemDefinitionEnum.DIVINE_ORB);
        BigDecimal divineAveragePrice = calculateAveragePriceForDivine(now, nowMinus12Hours, chaosOrbEntity, divineOrbEntity);
        saveDivineAveragePrice(divineOrbEntity, divineAveragePrice);

        ItemDefinitionEntity sextantEntity = itemDefinitionsService.findFirsItemDefinitionByItemDefinitionEnum(ItemDefinitionEnum.AWAKENED_SEXTANT);
        BigDecimal sextantAveragePrice = calculateAveragePriceForSextant(now, nowMinus12Hours, chaosOrbEntity, sextantEntity);
        saveSextantAveragePrice(sextantEntity, sextantAveragePrice);

        // add one for chaos as well, even though it's not calculated
        ItemDefinitionEntity chaosEntity = itemDefinitionsService.findFirsItemDefinitionByItemDefinitionEnum(ItemDefinitionEnum.CHAOS_ORB);
        saveChaosAveragePrice(chaosEntity);

        // save after getting all so that an exception can trigger if anything goes wrong
        currencyRatioService.saveAll(currencyRatioMap.values());
        publishCurrencyRatioRecalculatedEvent(currencyRatioMap);
    }

    private BigDecimal calculateAveragePriceForSextant(
            Timestamp now,
            Timestamp nowMinus12Hours,
            ItemDefinitionEntity chaosOrbEntity,
            ItemDefinitionEntity sextantEntity
    ) throws CurrencyRatioException {
        Collection<ItemEntity> pricesInBetweenDates = itemEntityService.getPricesForItemsBetweenDatesWithLimitAndOffset(
                sextantEntity.getId(),
                chaosOrbEntity.getId(),
                200,
                nowMinus12Hours,
                now,
                10,
                IMPOSED_LIMIT
        );

        BigDecimal lowerLimit = BigDecimal.valueOf(1);
        BigDecimal upperLimit = BigDecimal.valueOf(30);
        return getPriceAsBigDecimalAverage(pricesInBetweenDates, lowerLimit, upperLimit);
    }

    private BigDecimal calculateAveragePriceForDivine(Timestamp now,
                                                      Timestamp nowMinus12Hours,
                                                      ItemDefinitionEntity chaosOrbEntity,
                                                      ItemDefinitionEntity divineOrbEntity
    ) throws CurrencyRatioException {
        Collection<ItemEntity> pricesInBetweenDates = itemEntityService.getPricesForItemsBetweenDatesWithLimitAndOffset(
                divineOrbEntity.getId(),
                chaosOrbEntity.getId(),
                200,
                nowMinus12Hours,
                now,
                10,
                IMPOSED_LIMIT
        );

        BigDecimal lowerLimit = BigDecimal.valueOf(5);
        BigDecimal upperLimit = BigDecimal.valueOf(300);
        return getPriceAsBigDecimalAverage(pricesInBetweenDates, lowerLimit, upperLimit);
    }

    private BigDecimal getPriceAsBigDecimalAverage(
            Collection<ItemEntity> pricesInBetweenDates,
            BigDecimal lowerLimit, BigDecimal upperLimit
    ) throws CurrencyRatioException {
        pricesInBetweenDates.removeIf(next -> next.getPrice().compareTo(lowerLimit) < 0 || next.getPrice().compareTo(upperLimit) > 0);
        OptionalDouble averagePrice = pricesInBetweenDates.stream().mapToDouble(item -> item.getPrice().doubleValue()).average();

        if (averagePrice.isPresent()) {
            return BigDecimal.valueOf(averagePrice.getAsDouble());
        }
        throw new CurrencyRatioException("Unable to calculate price");
    }

    private void saveDivineAveragePrice(
            ItemDefinitionEntity divineOrbEntity,
            BigDecimal divineAveragePrice
    ) {
        CurrencyRatioEntity divineCurrencyRatio = new CurrencyRatioEntity();
        divineCurrencyRatio.setItemDefinitionEntity(divineOrbEntity);
        divineCurrencyRatio.setChaos(divineAveragePrice);
        currencyRatioMap.put(ItemDefinitionEnum.DIVINE_ORB, divineCurrencyRatio);
    }

    private void saveSextantAveragePrice(
            ItemDefinitionEntity sextantEntity,
            BigDecimal sextantAveragePrice
    ) {
        CurrencyRatioEntity sextantCurrencyRatio = new CurrencyRatioEntity();
        sextantCurrencyRatio.setItemDefinitionEntity(sextantEntity);
        sextantCurrencyRatio.setChaos(sextantAveragePrice);
        currencyRatioMap.put(ItemDefinitionEnum.AWAKENED_SEXTANT, sextantCurrencyRatio);
    }

    private void saveChaosAveragePrice(ItemDefinitionEntity chaosEntity) {
        CurrencyRatioEntity chaosCurrencyRatio = new CurrencyRatioEntity();
        chaosCurrencyRatio.setItemDefinitionEntity(chaosEntity);
        chaosCurrencyRatio.setChaos(BigDecimal.valueOf(1));
        currencyRatioMap.put(ItemDefinitionEnum.CHAOS_ORB, chaosCurrencyRatio);
    }
}
