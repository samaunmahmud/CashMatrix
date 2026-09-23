package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.service.MerchantNames;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantNamesTest {

    @Test
    void retailerDropsBranchWords() {
        assertThat(MerchantNames.retailer("TESCO STORES 2041")).isEqualTo("Tesco");
        assertThat(MerchantNames.retailer("Tesco Express")).isEqualTo("Tesco");
        assertThat(MerchantNames.retailer("SAINSBURY'S LOCAL")).isEqualTo("Sainsbury's");
        assertThat(MerchantNames.retailer("Tesco Extra Petrol")).isEqualTo("Tesco");
    }

    @Test
    void retailerKeepsNamesThatAreNotBranches() {
        assertThat(MerchantNames.retailer("Metro Bank")).isEqualTo("Metro Bank");
        assertThat(MerchantNames.retailer("British Gas")).isEqualTo("British Gas");
        assertThat(MerchantNames.retailer("Dishoom Kings Cross")).isEqualTo("Dishoom Kings Cross");
        assertThat(MerchantNames.retailer("NETFLIX.COM 866-579-7172")).isEqualTo("Netflix");
    }

    @Test
    void retailerNeverEmptiesAName() {
        assertThat(MerchantNames.retailer("Express")).isEqualTo("Express");
        assertThat(MerchantNames.retailer("LOCAL STORE")).isEqualTo("Local");
    }
}
