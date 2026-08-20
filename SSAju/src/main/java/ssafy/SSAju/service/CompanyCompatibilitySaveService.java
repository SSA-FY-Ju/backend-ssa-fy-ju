package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ssafy.SSAju.annotation.DistributedLock;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;

import java.util.Optional;

/**
 * CompanyCompatibility 저장을 (사용자, 프로필, 회사, 직무, 월) 단위 분산락으로 보호합니다.
 *
 * <p>{@link CompanyMatchingService}가 FastAPI/OpenAI 호출과 계산을 모두 마친 뒤에만 이
 * 메서드를 호출한다 — 락은 DB 저장이라는 짧고 예측 가능한 구간에만 걸리며, 걸리는 시간이
 * 들쭉날쭉한 외부 I/O는 락 밖에서 이미 끝난 상태다. 이 덕분에 락 임대시간(leaseTime, 기본
 * 5000ms)이 만료되기 전에 항상 끝나는 것이 보장된다(만료 자체가 원천적으로 어렵다).
 *
 * <p>이 클래스엔 {@code @Transactional}이 없다 — 락이 메서드 전체를 감싸고, 그 안의
 * {@code save()}는 Spring Data JPA 기본 트랜잭션으로 개별 실행된다. 그럼에도 임대시간 만료를
 * 완전히 배제할 수는 없으므로(DB 커넥션 풀 고갈 등 극단적 상황), UNIQUE 제약 위반을
 * "불가능한 상황"으로 가정하지 않고 재조회로 복구한다 — 바깥 트랜잭션이 없어 재조회를 오염시킬
 * 대상 자체가 없으므로 REQUIRES_NEW 같은 격리 장치 없이도 항상 안전하다
 * ({@code UserProfileProvider}/{@code SajuResultProvider}/{@code ConsultationSaveService}와
 * 동일한 패턴).
 *
 * <p>같은 클래스 내 self-invocation에서는 Spring AOP 프록시가 {@code @DistributedLock}을
 * 가로채지 못하므로, {@code CompanyMatchingService}와 분리된 별도 빈으로 존재한다.
 *
 * <p>동시에 완전히 동일한 (사용자, 프로필, 회사, 직무, 월) 요청이 여러 번 온 경우, 락 안에서
 * 완료된 기존 행을 재확인해 있으면 재사용한다 — FastAPI/OpenAI가 중복 호출되는 것은 감수하되
 * (더블체크락으로 막지 않음), DB에는 항상 정확히 1건만 남도록 보장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyCompatibilitySaveService {

    private final CompanyCompatibilityRepository companyCompatibilityRepository;

    // 락 키에 자유 입력 텍스트(companyName)를 ':'로 이어붙이면, 회사명 안에 ':'가 포함될 경우
    // 키 경계가 밀려 이론상 다른 (회사, 직무) 조합과 충돌할 수 있다. 회사명 앞에 길이를 붙여
    // 경계를 고정하면(예: "4_현대"), 같은 최종 문자열을 만들려면 길이 필드까지 조작해야 하므로
    // 실질적으로 충돌이 불가능해진다.
    @DistributedLock(key = "'lock:company-compatibility:' + #entity.user.id + ':' + #entity.userProfile.id + ':' "
            + "+ #entity.companyName.length() + '_' + #entity.companyName + ':' "
            + "+ #entity.targetRoleCategory + ':' + #entity.compatibilityMonth")
    public CompanyCompatibility saveWithLock(CompanyCompatibility entity, CompatibilityAnalysisData analysisData) {
        Optional<CompanyCompatibility> existing = findCompleted(entity);
        if (existing.isPresent()) {
            log.info("락 안에서 완료된 기존 행 재사용 (compatibilityId={})", existing.get().getId());
            return existing.get();
        }

        try {
            entity.assignResultJsonAndMarkCompleted(analysisData);
            CompanyCompatibility saved = companyCompatibilityRepository.save(entity);
            log.info("CompanyCompatibility 신규 저장 완료 (compatibilityId={})", saved.getId());
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.warn("CompanyCompatibility 삽입 중 UNIQUE 제약 위반(락 임대시간 만료 경합 추정) — 재조회: "
                    + "userProfileId={}, month={}", entity.getUserProfile().getId(), entity.getCompatibilityMonth(), e);
            return findCompleted(entity)
                    .orElseThrow(() -> new DataAccessException(
                            "CompanyCompatibility 경합 복구 실패: userProfileId="
                                    + entity.getUserProfile().getId()
                                    + ", month=" + entity.getCompatibilityMonth(), e));
        }
    }

    private Optional<CompanyCompatibility> findCompleted(CompanyCompatibility entity) {
        return companyCompatibilityRepository
                .findByUser_IdAndUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndCompatibilityMonthAndCompletedTrue(
                        entity.getUser().getId(), entity.getUserProfile().getId(),
                        entity.getCompanyName(), entity.getTargetRoleCategory(), entity.getCompatibilityMonth());
    }
}
