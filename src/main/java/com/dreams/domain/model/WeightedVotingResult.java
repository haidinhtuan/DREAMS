package com.dreams.domain.model;

/**
 * Result of a weighted voting round.
 * Instead of simple majority, votes are weighted by each LDM's local impact score.
 *
 * Weighted approval = Σ(vote_i × weight_i) / Σ(weight_i)
 * where vote_i ∈ {1.0 (approve), 0.0 (reject)} and weight_i = max(I_local_i, ε)
 */
public record WeightedVotingResult(
        double weightedApprovalScore,
        long approveCount,
        long rejectCount,
        long totalVoters,
        boolean approved
) {
    public static WeightedVotingResult fromSimpleMajority(long approved, long rejected, long total) {
        double score = total > 0 ? (double) approved / total : 0.0;
        return new WeightedVotingResult(score, approved, rejected, total, approved > total / 2);
    }
}
