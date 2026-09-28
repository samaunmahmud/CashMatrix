package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.config.ResourceNotFoundException;
import com.expensetracker.expensetracker.dto.CategoryRuleResponse;
import com.expensetracker.expensetracker.dto.TransactionResponse;
import com.expensetracker.expensetracker.dto.TransactionUpdateRequest;
import com.expensetracker.expensetracker.dto.TransactionUpdateResponse;
import com.expensetracker.expensetracker.model.BankAccount;
import com.expensetracker.expensetracker.model.CategoryRule;
import com.expensetracker.expensetracker.model.Transaction;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.CategoryRuleRepository;
import com.expensetracker.expensetracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Re-filing transactions: the user's own category and note on one transaction, and rules that
 * file every transaction from a retailer the same way.
 */
@Service
@RequiredArgsConstructor
public class CategoryRuleService {

    static final int MAX_RULES = 200;

    private final CategoryRuleRepository ruleRepository;
    private final TransactionRepository transactionRepository;
    private final BankAccountRepository bankAccountRepository;

    /** The key rules are matched on: the retailer, ignoring case. */
    public static String keyOf(String rawName) {
        return MerchantNames.retailer(rawName).toLowerCase(Locale.ROOT).trim();
    }

    /** The user's rules, retailer key to category. */
    @Transactional(readOnly = true)
    public Map<String, String> rulesFor(User user) {
        Map<String, String> rules = new HashMap<>();
        ruleRepository.findByUserOrderByMerchantNameAsc(user).forEach(rule -> rules.put(rule.getMerchantKey(), rule.getCategory()));
        return rules;
    }

    @Transactional(readOnly = true)
    public List<CategoryRuleResponse> list(User user) {
        return ruleRepository.findByUserOrderByMerchantNameAsc(user).stream().map(CategoryRuleResponse::from).toList();
    }

    /** Stops filing new transactions by the rule. Transactions it already filed keep their category. */
    @Transactional
    public void delete(User user, Long id) {
        ruleRepository.delete(ruleRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Rule not found")));
    }

    /**
     * Sets a transaction's category and note. With applyToRetailer, every other transaction from the
     * same retailer gets the category too, and so will new ones; an empty category then removes the rule
     * and puts those transactions back to the bank's category.
     */
    @Transactional
    public TransactionUpdateResponse update(User user, Long id, TransactionUpdateRequest request) {
        Transaction tx = transactionRepository.findByIdAndBankAccountUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        String category = tidy(request.getCategory());
        tx.setUserCategory(category);
        String note = request.getNote() == null ? null : request.getNote().trim();
        tx.setNote(note == null || note.isEmpty() ? null : note);

        int alsoUpdated = 0;
        if (request.isApplyToRetailer()) {
            String key = keyOf(tx.getName());
            if (key.isEmpty()) throw new IllegalArgumentException("This transaction has no retailer name to make a rule from");
            saveRule(user, key, MerchantNames.retailer(tx.getName()), category);
            alsoUpdated = refile(user, key, category, tx.getId());
        }
        return new TransactionUpdateResponse(TransactionResponse.from(tx), alsoUpdated);
    }

    private void saveRule(User user, String key, String retailer, String category) {
        CategoryRule existing = ruleRepository.findByUserAndMerchantKey(user, key).orElse(null);
        if (category == null) {
            if (existing != null) ruleRepository.delete(existing);
            return;
        }
        if (existing == null) {
            if (ruleRepository.findByUserOrderByMerchantNameAsc(user).size() >= MAX_RULES) {
                throw new IllegalArgumentException("You can have up to " + MAX_RULES + " retailer rules");
            }
            existing = new CategoryRule();
            existing.setUser(user);
            existing.setMerchantKey(key);
        }
        existing.setMerchantName(retailer);
        existing.setCategory(category);
        ruleRepository.save(existing);
    }

    /** Gives the user's other transactions from a retailer the category. @return how many changed */
    private int refile(User user, String key, String category, Long exceptId) {
        List<BankAccount> accounts = bankAccountRepository.findByUser(user);
        int changed = 0;
        for (Transaction other : transactionRepository.findByBankAccountInOrderByTransactionDateDesc(accounts)) {
            if (other.getId().equals(exceptId) || !key.equals(keyOf(other.getName()))) continue;
            if (Objects.equals(other.getUserCategory(), category)) continue;
            other.setUserCategory(category);
            changed++;
        }
        return changed;
    }

    /** Trims and collapses inner spaces, so a category matches its budget; empty means none. */
    private static String tidy(String category) {
        if (category == null) return null;
        String tidied = category.trim().replaceAll("\\s+", " ");
        return tidied.isEmpty() ? null : tidied;
    }
}
