package com.braintreepayments.api;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;

import com.braintreepayments.api.exceptions.BraintreeException;
import com.braintreepayments.api.exceptions.ConfigurationException;
import com.braintreepayments.api.interfaces.ConfigurationListener;
import com.braintreepayments.api.interfaces.PaymentMethodNonceCallback;
import com.braintreepayments.api.models.BraintreeRequestCodes;
import com.braintreepayments.api.models.Configuration;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.braintreepayments.api.models.VisaCheckoutBuilder;
import com.braintreepayments.api.models.VisaCheckoutConfiguration;
import com.visa.checkout.VisaLibrary;
import com.visa.checkout.VisaMcomLibrary;
import com.visa.checkout.VisaMerchantInfo;
import com.visa.checkout.VisaMerchantInfo.AcceptedCardBrands;
import com.visa.checkout.VisaMerchantInfo.MerchantDataLevel;
import com.visa.checkout.VisaPaymentInfo;
import com.visa.checkout.VisaPaymentSummary;
import com.visa.checkout.utils.VisaEnvironmentConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Used to create and tokenize Visa Checkout. For more information see the
 * <a href="https://developers.braintreepayments.com/guides/visa-checkout/overview">documentation</a>
 */
public class VisaCheckout {

    /**
     * Creates the Visa Checkout library.
     * @param fragment {@link BraintreeFragment}
     */
    public static void createVisaCheckoutLibrary(final BraintreeFragment fragment) {
        if (!VisaCheckoutConfiguration.isVisaPackageAvailable()) {
            fragment.postCallback(new ConfigurationException("Visa Checkout SDK is not available"));
            return;
        }

        fragment.waitForConfiguration(new ConfigurationListener() {
            @Override
            public void onConfigurationFetched(Configuration configuration) {
                VisaCheckoutConfiguration visaCheckoutConfiguration = configuration.getVisaCheckout();
                if (!visaCheckoutConfiguration.isEnabled()) {
                    fragment.postCallback(new ConfigurationException("Visa Checkout is not enabled."));
                    return;
                }

                VisaEnvironmentConfig visaEnvironmentConfig = createVisaEnvironmentConfig(configuration);

                VisaMcomLibrary visaMcomLibrary = VisaMcomLibrary.getLibrary(fragment.getActivity(),
                        visaEnvironmentConfig);
                fragment.postVisaCheckoutLibraryCallback(visaMcomLibrary);
            }
        });
    }

    /**
     * Starts Visa Checkout to authorize a payment from the customer.
     * @param fragment {@link BraintreeFragment}
     * @param visaPaymentInfo {@link VisaPaymentInfo} Used to customize the authorization process. Braintree
     * requires some properties set, and will set those properties if they are empty.
     * </p>
     * The properties Braintree will fill in are:
     * <ul>
     *     <li>{@link VisaMerchantInfo#setMerchantApiKey(String)}</li>
     *     <li>{@link VisaMerchantInfo#setAcceptedCardBrands(List)}</li>
     *     <li>{@link VisaPaymentInfo#setExternalClientId(String)} </li>
     * </ul>
     * Braintree will also overwrite {@link VisaMerchantInfo#setDataLevel(MerchantDataLevel)} to {@link MerchantDataLevel#FULL}.
     */
    public static void authorize(final BraintreeFragment fragment, final VisaPaymentInfo visaPaymentInfo) {
        fragment.waitForConfiguration(new ConfigurationListener() {

            @Override
            public void onConfigurationFetched(Configuration configuration) {
                VisaCheckoutConfiguration visaCheckoutConfiguration = configuration.getVisaCheckout();
                VisaMerchantInfo visaMerchantInfo = visaPaymentInfo.getVisaMerchantInfo();
                if (visaMerchantInfo == null) {
                    visaMerchantInfo = new VisaMerchantInfo();
                }

                if (TextUtils.isEmpty(visaMerchantInfo.getMerchantApiKey())) {
                    visaMerchantInfo.setMerchantApiKey(visaCheckoutConfiguration.getApiKey());
                }

                if (TextUtils.isEmpty(visaPaymentInfo.getExternalClientId())) {
                    visaPaymentInfo.setExternalClientId(visaCheckoutConfiguration.getExternalClientId());
                }

                visaMerchantInfo.setDataLevel(MerchantDataLevel.FULL);

                if (visaMerchantInfo.getAcceptedCardBrands() == null || visaMerchantInfo.getAcceptedCardBrands().isEmpty()) {
                    Set<String> supportedCardTypes = configuration.getCardConfiguration().getSupportedCardTypes();
                    List<AcceptedCardBrands> acceptedCardBrands = new ArrayList<>();
                    for (String supportedCardType : supportedCardTypes) {
                        switch (supportedCardType) {
                            case "Visa":
                                acceptedCardBrands.add(AcceptedCardBrands.ELECTRON);
                                acceptedCardBrands.add(AcceptedCardBrands.VISA);
                                break;
                            case "MasterCard":
                                acceptedCardBrands.add(AcceptedCardBrands.MASTERCARD);
                                break;
                            case "Discover":
                                acceptedCardBrands.add(AcceptedCardBrands.DISCOVER);
                                break;
                            case "American Express":
                                acceptedCardBrands.add(AcceptedCardBrands.AMEX);
                                break;
                        }
                    }
                    visaMerchantInfo.setAcceptedCardBrands(acceptedCardBrands);
                }

                visaPaymentInfo.setVisaMerchantInfo(visaMerchantInfo);
                BraintreeVisaCheckoutResultActivity.sVisaEnvironmentConfig = createVisaEnvironmentConfig(configuration);
                BraintreeVisaCheckoutResultActivity.sVisaPaymentInfo = visaPaymentInfo;

                Intent visaCheckoutResultActivity = new Intent(fragment.getActivity(),
                        BraintreeVisaCheckoutResultActivity.class);
                fragment.startActivityForResult(visaCheckoutResultActivity,
                        BraintreeRequestCodes.VISA_CHECKOUT);
            }
        });
    }

    static VisaEnvironmentConfig createVisaEnvironmentConfig(Configuration configuration) {
        VisaEnvironmentConfig visaEnvironmentConfig = VisaEnvironmentConfig.SANDBOX;
        if ("production".equals(configuration.getEnvironment())) {
            visaEnvironmentConfig = VisaEnvironmentConfig.PRODUCTION;
        }

        visaEnvironmentConfig.setMerchantApiKey(configuration.getVisaCheckout().getApiKey());
        visaEnvironmentConfig.setVisaCheckoutRequestCode(BraintreeRequestCodes.VISA_CHECKOUT);
        return visaEnvironmentConfig;

    }

    static void onActivityResult(BraintreeFragment fragment, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_CANCELED) {
            fragment.postCancelCallback(BraintreeRequestCodes.VISA_CHECKOUT);
            fragment.sendAnalyticsEvent("visacheckout.activityresult.canceled");
        } else if (resultCode == Activity.RESULT_OK && data != null) {
            VisaPaymentSummary visaPaymentSummary = data.getParcelableExtra(VisaLibrary.PAYMENT_SUMMARY);
            tokenize(fragment, visaPaymentSummary);
            fragment.sendAnalyticsEvent("visacheckout.activityresult.ok");
        } else {
            fragment.postCallback(new BraintreeException("Visa Checkout responded with resultCode=" + resultCode));
            fragment.sendAnalyticsEvent("visacheckout.activityresult.failed");
        }
    }

    static void tokenize(final BraintreeFragment fragment, final VisaPaymentSummary visaPaymentSummary) {
        TokenizationClient.tokenize(fragment, new VisaCheckoutBuilder(visaPaymentSummary),
                new PaymentMethodNonceCallback() {
                    @Override
                    public void success(PaymentMethodNonce paymentMethodNonce) {
                        fragment.postCallback(paymentMethodNonce);
                        fragment.sendAnalyticsEvent("visacheckout.tokenize.succeeded");
                    }

                    @Override
                    public void failure(Exception exception) {
                        fragment.postCallback(exception);
                        fragment.sendAnalyticsEvent("visacheckout.tokenize.failed");
                    }
                });
    }
}