package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.CareerFortune;
import ssafy.SSAju.career.entity.HiddenStemData;
import ssafy.SSAju.career.entity.SajuFullData;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.TenGodData;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.repository.SajuResultRepository;

import java.util.List;

/**
 * SajuResult 신규 저장 시 자식 엔티티를 단일 트랜잭션으로 보호.
 *
 * insertOrIgnore(AUTO COMMIT) 성공 후 자식 저장이 단일 트랜잭션으로 보호되어,
 * 자식 저장 실패 시 롤백되어 root만 남는 불일치 상태를 방지.
 *
 * SajuResult는 (user, userProfile) 기준으로 불변(immutable)입니다.
 * 동일 생년월일·시각으로 요청된 결과는 기존 row를 재사용하며,
 * 삭제 후 재생성하는 패턴은 사용하지 않습니다.
 *
 * ⚠️ 로그 작성 규칙:
 * - userId만 사용 (birthDate, birthTime 등 개인정보 절대 금지)
 */
@Service
@RequiredArgsConstructor
public class SajuResultWriteService {

    private final SajuResultRepository sajuResultRepository;

    /**
     * insertOrIgnore로 삽입된 새 SajuResult에 자식 엔티티를 붙여 저장.
     *
     * insertOrIgnore(AUTO COMMIT) 성공 후 자식 저장이 단일 트랜잭션으로 보호되어,
     * 자식 저장 실패 시 롤백되어 root만 남는 불일치 상태를 방지.
     */
    @Transactional
    public SajuResult saveNewResultWithChildren(SajuResult detached, SajuResult source) {
        // 트랜잭션 내에서 재조회 → managed 엔티티 확보 (detached 엔티티의 PersistentBag 조작 방지)
        SajuResult saved = sajuResultRepository.findById(detached.getId())
                .orElseThrow(() -> new DataAccessException(ErrorMessageConstants.SAJU_RESULT_ACCESS_FAILED.getMessage()));

        SajuFullData srcFullData = source.getSajuFullData();
        if (srcFullData != null) {
            saved.assignSajuFullData(SajuFullData.builder()
                    .sajuResult(saved)
                    .yearPillar(srcFullData.getYearPillar())
                    .monthPillar(srcFullData.getMonthPillar())
                    .dayPillar(srcFullData.getDayPillar())
                    .hourPillar(srcFullData.getHourPillar())
                    .dayMaster(srcFullData.getDayMaster())
                    .dayMasterElement(srcFullData.getDayMasterElement())
                    .fiveElements(srcFullData.getFiveElements())
                    .solarCorrection(srcFullData.getSolarCorrection())
                    .build());
        }

        List<TenGodData> tenGods = source.getTenGodDataList().stream()
                .map(e -> TenGodData.builder()
                        .sajuResult(saved)
                        .tenGodName(e.getTenGodName())
                        .score(e.getScore())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        List<HiddenStemData> hiddenStems = source.getHiddenStemDataList().stream()
                .map(e -> HiddenStemData.builder()
                        .sajuResult(saved)
                        .earthlyBranch(e.getEarthlyBranch())
                        .hiddenStem(e.getHiddenStem())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        saved.assignTenGodData(tenGods);
        saved.assignHiddenStemData(hiddenStems);

        CareerFortune cf = source.getCareerFortune();
        if (cf != null) {
            saved.assignCareerFortune(CareerFortune.builder()
                    .sajuResult(saved)
                    .favoredPeriod(cf.getFavoredPeriod())
                    .confidenceScore(cf.getConfidenceScore())
                    .reasoning(cf.getReasoning())
                    .build());
        }

        return sajuResultRepository.save(saved);
    }
}
