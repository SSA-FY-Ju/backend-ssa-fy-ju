package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.converter.AnalysisSnapshotCodec;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.mapper.AnalysisSnapshotMapper;
import ssafy.SSAju.dto.response.AnalysisDetailResponse;
import ssafy.SSAju.dto.response.MyPageResponse;
import ssafy.SSAju.dto.response.UserAnalysisDto;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.SajuResultNotFoundException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.AnalysisHistoryRepository;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final SajuRepository sajuRepository;
    private final AnalysisSnapshotCodec snapshotCodec;
    private final AnalysisSnapshotMapper analysisSnapshotMapper;

    @Transactional(readOnly = true)
    public MyPageResponse getMyPage(Long userId, AnalysisType type, int page, int size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        String typeStr = type != null ? type.name() : null;
        List<UserAnalysisDto> analyses = (typeStr != null)
                ? analysisHistoryRepository.findAllByUserIdAndType(userId, typeStr, page, size)
                : analysisHistoryRepository.findAllByUserId(userId, page, size);

        long total = (typeStr != null)
                ? analysisHistoryRepository.countAllByUserIdAndType(userId, typeStr)
                : analysisHistoryRepository.countAllByUserId(userId);
        int totalPages = (int) Math.ceil((double) total / size);

        MyPageResponse.UserProfileData profileData = new MyPageResponse.UserProfileData(
                user.getId(), user.getName(), user.getEmail(),
                user.getCreatedAt(), user.getLastLoginAt());

        return new MyPageResponse(profileData, analyses,
                new MyPageResponse.PaginationInfo(page, size, total, totalPages));
    }

    /**
     * 저장된 결과 스냅샷만 읽어 상세를 돌려준다.
     *
     * <p>없는 ID·타인의 ID·요청 유형과 다른 ID를 모두 같은 미존재 오류로 처리한다 — 셋을 구분해
     * 알려주면 남의 분석이 존재한다는 사실이 드러난다.
     */
    @Transactional(readOnly = true)
    public AnalysisDetailResponse getAnalysisDetail(Long userId, Long analysisId, AnalysisType type) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        Saju saju = sajuRepository.findByIdAndUserIdAndAnalysisType(analysisId, userId, type)
                .orElseThrow(() -> new SajuResultNotFoundException("분석 결과를 찾을 수 없습니다."));

        AnalysisPayload.Input input = snapshotCodec.readInput(type, saju.getInputJson());
        AnalysisPayload.Result result = snapshotCodec.readResult(type, saju.getResultJson());

        return analysisSnapshotMapper.toDetailResponse(
                saju, user.getName(), saju.getManse().getUserProfile().getBirthDate(), input, result);
    }
}
