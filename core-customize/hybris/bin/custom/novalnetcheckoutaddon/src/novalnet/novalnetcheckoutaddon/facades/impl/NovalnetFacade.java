/*
 *
 * @author    Novalnet AG
 * @copyright Copyright by Novalnet
 * @license   https://www.novalnet.de/payment-plugins/kostenlos/lizenz
 *
 * If you have found this script useful a small
 * recommendation as well as a comment on merchant form
 * would be greatly appreciated.
 *
 */
package novalnet.novalnetcheckoutaddon.facades;

import java.lang.*;
import java.io.*;

import java.util.Map;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;

import javax.annotation.Resource;
import java.math.BigDecimal;

import de.hybris.platform.acceleratorfacades.order.AcceleratorCheckoutFacade;
import de.hybris.platform.acceleratorfacades.order.impl.DefaultAcceleratorCheckoutFacade;

import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;

import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.enums.PaymentStatus;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.order.payment.PaymentModeModel;

import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.orderhistory.model.OrderHistoryEntryModel;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commerceservices.enums.CustomerType;
import de.hybris.platform.servicelayer.dto.converter.Converter;

import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.PaymentModeService;

import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.SearchResult;

import de.hybris.novalnet.core.model.NovalnetPaymentInfoModel;
import de.hybris.novalnet.core.model.NovalnetPaymentRefInfoModel;
import de.hybris.novalnet.core.model.NovalnetDirectDebitSepaPaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetGuaranteedDirectDebitSepaPaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetGuaranteedInvoicePaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetPayPalPaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetCreditCardPaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetInvoicePaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetPrepaymentPaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetBarzahlenPaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetInstantBankTransferPaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetOnlineBankTransferPaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetBancontactPaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetMultibancoPaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetIdealPaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetEpsPaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetGiropayPaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetPrzelewy24PaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetPostFinanceCardPaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetPostFinancePaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetCallbackInfoModel;

import de.hybris.platform.core.model.order.payment.PaymentModeModel;

import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.io.ObjectOutputStream;
import java.net.URL;

import org.apache.log4j.Logger;
import org.xml.sax.SAXException;
import org.json.JSONObject;

import java.net.MalformedURLException;

import java.nio.charset.StandardCharsets;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.Base64;

/**
 * NovalnetFacade
 */
public class NovalnetFacade extends DefaultAcceleratorCheckoutFacade {
	
	 private static final Logger LOGGER = Logger.getLogger(NovalnetFacade.class);

    @Resource(name = "cartService")
    private CartService cartService;

    @Resource
    private PaymentModeService paymentModeService;

    private FlexibleSearchService flexibleSearchService;

    @Resource
    private Converter<AddressData, AddressModel> addressReverseConverter;


    /**
     * Get order model
     *
     * @param orderCode Order code of the order
     * @return SearchResult
     */
    public List<OrderModel> getOrderInfoModel(String orderCode) {
        // Initialize StringBuilder
        StringBuilder query = new StringBuilder();

        // Select query for fetch OrderModel
        query.append("SELECT {pk} from {" + OrderModel._TYPECODE + "} where {" + OrderModel.CODE
                + "} = ?code");
        FlexibleSearchQuery executeQuery = new FlexibleSearchQuery(query.toString());

        // Add query parameter
        executeQuery.addQueryParameter("code", orderCode);

        // Execute query
        SearchResult<OrderModel> result = getFlexibleSearchService().search(executeQuery);
        return result.getResult();
    }

	/**
     * Update order status
     *
     * @param orderCode Order code of the order
     * @param paymentInfoModel payment configurations
     */
    public void updateOrderStatus(String orderCode, NovalnetPaymentInfoModel paymentInfoModel) {
        List<OrderModel> orderInfoModel = getOrderInfoModel(orderCode);

        OrderModel orderModel = this.getModelService().get(orderInfoModel.get(0).getPk());
        final BaseStoreModel baseStore = this.getBaseStoreModel();
        orderModel.setStatus(getOrderStatus(paymentInfoModel, baseStore));
        
        final String paymentMethod = paymentInfoModel.getPaymentProvider();        
        String[] bankPayments = {"novalnetInvoice", "novalnetPrepayment", "novalnetBarzahlen"};
		boolean isInvoicePrepayment = Arrays.asList(bankPayments).contains(paymentMethod);
		String[] pendingStatusCode = {"ON_HOLD","PENDING"};

		// Check for payment pending payments
		if(isInvoicePrepayment || Arrays.asList(pendingStatusCode).contains(paymentInfoModel.getPaymentGatewayStatus()))
		{
			orderModel.setPaymentStatus(PaymentStatus.NOTPAID);
		}
		else
		{
			// Update the payment status for completed payments
			orderModel.setPaymentStatus(PaymentStatus.PAID);
		}
        
        this.getModelService().save(orderModel);

    }
	
	/**
     * Get Payment model
     *
     * @param paymentInfo info of the payment
     * @return paymentModel
     */
    public NovalnetPaymentInfoModel getPaymentModel(final List<NovalnetPaymentInfoModel> paymentInfo) {
        final NovalnetPaymentInfoModel paymentModel = this.getModelService().get(paymentInfo.get(0).getPk());
        return paymentModel;
    }
	
	/**
     * Update Cancel order status
     *
     * @param orderCode Order code of the order
     */
    public void updateCancelStatus(String orderCode) {
        List<OrderModel> orderInfoModel = getOrderInfoModel(orderCode);

        // Update OrderHistoryEntries
        OrderModel orderModel = this.getModelService().get(orderInfoModel.get(0).getPk());

        final BaseStoreModel baseStore = this.getBaseStoreModel();
        OrderStatus orderStatus = OrderStatus.CANCELLED;

        orderModel.setStatus(orderStatus);

        this.getModelService().save(orderModel);

    }
    
    
	/**
     * Send request
     *
     * @param url to which the requst to be sent
     * @param jsonString the string to be passed in the request
     * @return response
     */
    public StringBuilder sendRequest(String url, String jsonString) {
        final BaseStoreModel baseStore = this.getBaseStoreModel();
        String password = baseStore.getNovalnetPaymentAccessKey().trim();
        StringBuilder response = new StringBuilder();

        try {
            String urly = url;
            URL obj = new URL(urly);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            byte[] postData = jsonString.getBytes(StandardCharsets.UTF_8);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Charset", "utf-8");
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("X-NN-Access-Key", Base64.getEncoder().encodeToString(password.getBytes()));

            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.write(postData);
            wr.flush();
            wr.close();

            int responseCode = con.getResponseCode();
            BufferedReader iny = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String output;


            while ((output = iny.readLine()) != null) {
                response.append(output);
            }
            iny.close();
        } catch (MalformedURLException ex) {
            LOGGER.error("MalformedURLException ", ex);
        } catch (IOException ex) {
            LOGGER.error("IOException ", ex);
        }

        return response;

    }

    /**
     * Insert Payment Reference details
     *
     * @param response       Response of the transaction
     * @param customerNo     Customer ID
     * @param currentPayment Current payment code
     */
    public void handleReferenceTransactionInfo(StringBuilder response, String customerNo, String currentPayment) {
        JSONObject tomJsonObject = new JSONObject(response.toString());
        JSONObject transactionJsonObject = tomJsonObject.getJSONObject("transaction");
        JSONObject paymentDataJsonObject = transactionJsonObject.getJSONObject("payment_data");
        // Create and update NovalnetPaymentRefInfoModel
        NovalnetPaymentRefInfoModel novalnetPaymentRefInfo = new NovalnetPaymentRefInfoModel();
        long customerID = Long.parseLong(customerNo);
        long transactionID = Long.parseLong(transactionJsonObject.get("tid").toString());

        novalnetPaymentRefInfo.setCustomerNo(customerID);
        novalnetPaymentRefInfo.setPaymentType(currentPayment);
        novalnetPaymentRefInfo.setReferenceTransaction(false);
        novalnetPaymentRefInfo.setOrginalTid(transactionID);
        novalnetPaymentRefInfo.setToken(paymentDataJsonObject.get("token").toString());
        if ("novalnetCreditCard".equals(currentPayment) ) {
			String expiryDate = (paymentDataJsonObject.get("card_expiry_month").toString().length() == 1) ? "0" + paymentDataJsonObject.get("card_expiry_month").toString() : paymentDataJsonObject.get("card_expiry_month").toString();
            novalnetPaymentRefInfo.setCardType(paymentDataJsonObject.get("card_brand").toString());
            novalnetPaymentRefInfo.setCardHolder(paymentDataJsonObject.get("card_holder").toString());
            novalnetPaymentRefInfo.setMaskedCardNumber(paymentDataJsonObject.get("card_number").toString());
            novalnetPaymentRefInfo.setExpiryDate(expiryDate + " / " + paymentDataJsonObject.get("card_expiry_year").toString().substring(paymentDataJsonObject.get("card_expiry_year").toString().length() - 2));
        } else if ("novalnetDirectDebitSepa".equals(currentPayment)) {
            novalnetPaymentRefInfo.setMaskedAccountIban(paymentDataJsonObject.get("iban").toString());
            novalnetPaymentRefInfo.setAccountHolder(paymentDataJsonObject.get("account_holder").toString());
        } else if ("novalnetPayPal".equals(currentPayment)) {
			if (paymentDataJsonObject.has("paypal_transaction_id")) {
				novalnetPaymentRefInfo.setPaypalTransactionID(paymentDataJsonObject.get("paypal_transaction_id").toString());
			}
			if (paymentDataJsonObject.has("paypal_account")) {
				novalnetPaymentRefInfo.setPaypalEmailID(paymentDataJsonObject.get("paypal_account").toString());
			}
        }
        this.getModelService().save(novalnetPaymentRefInfo);

    }

	/**
     * Send Get order status
     *
     * @param paymentInfoModel paymnet info model of the selected payment
     * @param baseStore store configurations
     * @return OrderStatus
     */
    public OrderStatus getOrderStatus(NovalnetPaymentInfoModel paymentInfoModel, BaseStoreModel baseStore) {
        final String paymentMethod = paymentInfoModel.getPaymentProvider();
        PaymentModeModel paymentModeModel = paymentModeService.getPaymentModeForCode(paymentMethod);
        
        if ("novalnetCreditCard".equals(paymentMethod)) {
            NovalnetCreditCardPaymentModeModel novalnetPaymentMethod = (NovalnetCreditCardPaymentModeModel) paymentModeModel;
            if ("ON_HOLD".equals(paymentInfoModel.getPaymentGatewayStatus())) {
                return OrderStatus.PAYMENT_AUTHORIZED;
            }
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetDirectDebitSepa".equals(paymentMethod)) {

            NovalnetDirectDebitSepaPaymentModeModel novalnetPaymentMethod = (NovalnetDirectDebitSepaPaymentModeModel) paymentModeModel;

            if ("ON_HOLD".equals(paymentInfoModel.getPaymentGatewayStatus())) {
                return OrderStatus.PAYMENT_AUTHORIZED;
            }
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetGuaranteedDirectDebitSepa".equals(paymentMethod)) {

            NovalnetGuaranteedDirectDebitSepaPaymentModeModel novalnetPaymentMethod = (NovalnetGuaranteedDirectDebitSepaPaymentModeModel) paymentModeModel;
            // Guarantee pending status
            if ("PENDING".equals(paymentInfoModel.getPaymentGatewayStatus())) {
                return OrderStatus.PAYMENT_NOT_CAPTURED;
            }
            if ("ON_HOLD".equals(paymentInfoModel.getPaymentGatewayStatus())) {
                return OrderStatus.PAYMENT_AUTHORIZED;
            }
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetInvoice".equals(paymentMethod)) {
            NovalnetInvoicePaymentModeModel novalnetPaymentMethod = (NovalnetInvoicePaymentModeModel) paymentModeModel;
            if ("ON_HOLD".equals(paymentInfoModel.getPaymentGatewayStatus())) {
                return OrderStatus.PAYMENT_AUTHORIZED;
            }
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetGuaranteedInvoice".equals(paymentMethod)) {
            NovalnetGuaranteedInvoicePaymentModeModel novalnetPaymentMethod = (NovalnetGuaranteedInvoicePaymentModeModel) paymentModeModel;
            // Guarantee pending status
            if ("PENDING".equals(paymentInfoModel.getPaymentGatewayStatus())) {
                return OrderStatus.PAYMENT_NOT_CAPTURED;
            }
            if ("ON_HOLD".equals(paymentInfoModel.getPaymentGatewayStatus())) {
                return OrderStatus.PAYMENT_AUTHORIZED;
            }
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetPrepayment".equals(paymentMethod)) {
            NovalnetPrepaymentPaymentModeModel novalnetPaymentMethod = (NovalnetPrepaymentPaymentModeModel) paymentModeModel;
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetMultibanco".equals(paymentMethod)) {
            NovalnetMultibancoPaymentModeModel novalnetPaymentMethod = (NovalnetMultibancoPaymentModeModel) paymentModeModel;
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetBarzahlen".equals(paymentMethod)) {
            NovalnetBarzahlenPaymentModeModel novalnetPaymentMethod = (NovalnetBarzahlenPaymentModeModel) paymentModeModel;
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetPayPal".equals(paymentMethod)) {
            NovalnetPayPalPaymentModeModel novalnetPaymentMethod = (NovalnetPayPalPaymentModeModel) paymentModeModel;
            // PayPal pending status
            if ("PENDING".equals(paymentInfoModel.getPaymentGatewayStatus())) {
                return OrderStatus.PAYMENT_NOT_CAPTURED;
            }
            if ("ON_HOLD".equals(paymentInfoModel.getPaymentGatewayStatus())) {
                return OrderStatus.PAYMENT_AUTHORIZED;
            }
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetInstantBankTransfer".equals(paymentMethod)) {
            NovalnetInstantBankTransferPaymentModeModel novalnetPaymentMethod = (NovalnetInstantBankTransferPaymentModeModel) paymentModeModel;
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetOnlineBankTransfer".equals(paymentMethod)) {
            NovalnetOnlineBankTransferPaymentModeModel novalnetPaymentMethod = (NovalnetOnlineBankTransferPaymentModeModel) paymentModeModel;
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        }  else if ("novalnetBancontact".equals(paymentMethod)) {
			NovalnetBancontactPaymentModeModel novalnetPaymentMethod = (NovalnetBancontactPaymentModeModel) paymentModeModel;
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetPostFinanceCard".equals(paymentMethod)) {
            NovalnetPostFinanceCardPaymentModeModel novalnetPaymentMethod = (NovalnetPostFinanceCardPaymentModeModel) paymentModeModel;
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetPostFinance".equals(paymentMethod)) {
            NovalnetPostFinancePaymentModeModel novalnetPaymentMethod = (NovalnetPostFinancePaymentModeModel) paymentModeModel;
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetIdeal".equals(paymentMethod)) {
            NovalnetIdealPaymentModeModel novalnetPaymentMethod = (NovalnetIdealPaymentModeModel) paymentModeModel;
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetEps".equals(paymentMethod)) {
            NovalnetEpsPaymentModeModel novalnetPaymentMethod = (NovalnetEpsPaymentModeModel) paymentModeModel;
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetGiropay".equals(paymentMethod)) {
            NovalnetGiropayPaymentModeModel novalnetPaymentMethod = (NovalnetGiropayPaymentModeModel) paymentModeModel;
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        } else if ("novalnetPrzelewy24".equals(paymentMethod)) {
            NovalnetPrzelewy24PaymentModeModel novalnetPaymentMethod = (NovalnetPrzelewy24PaymentModeModel) paymentModeModel;

            // Payment pending status
            if ("PENDING".equals(paymentInfoModel.getPaymentGatewayStatus())) {
                return OrderStatus.PAYMENT_NOT_CAPTURED;
            }
            return novalnetPaymentMethod.getNovalnetOrderSuccessStatus();
        }

        return OrderStatus.COMPLETED;
    }
	
	/**
     * Save data in database
     *
     * @param billingAddress billing address of teh order
     * @param cartModel cart details of the order
     */
    public void saveData(AddressModel billingAddress, final CartModel cartModel) {
        this.getModelService().saveAll(billingAddress, cartModel);
    }
    
    /**
     * Save data in database
     *
     * @param orderCode Order code of the order
     * @return orderModel
     */
    public OrderModel getOrder(String orderCode) {
        List<OrderModel> orderInfoModel = getOrderInfoModel(orderCode);

        // Update OrderHistoryEntries
        OrderModel orderModel = this.getModelService().get(orderInfoModel.get(0).getPk());
        
        return orderModel;
    }
	
	/**
     * Save order data and initiate the order
     *
     * @param orderComments order comments
     * @param currentPayment current selected payment
     * @param transactionStatus status of transaction
     * @param orderAmountCent order amount in cents
     * @param currency order currency
     * @param transactionID transaction id
     * @param email customer email
     * @param addressData order addreess data
     * @param bankDetails  transaction bank details
     * @return OrderData 
     */
    public OrderData saveOrderData(String orderComments, String currentPayment, String transactionStatus, int orderAmountCent, String currency, String transactionID, String email, AddressData addressData, String bankDetails) throws InvalidCartException {
		final CartModel cartModel = getCart();
		
		final UserModel currentUser = getCurrentUserForCheckout();

		final BaseStoreModel baseStore = this.getBaseStoreModel();
		
		String backendTransactionComments = orderComments.replace("<br/>", " ");
		
		AddressModel billingAddress = this.getModelService().create(AddressModel.class);
		billingAddress = addressReverseConverter.convert(addressData, billingAddress);
		billingAddress.setEmail(email);
		billingAddress.setOwner(cartModel);
		
		NovalnetPaymentInfoModel paymentInfoModel = new NovalnetPaymentInfoModel();
		paymentInfoModel.setBillingAddress(billingAddress);
		paymentInfoModel.setPaymentEmailAddress(email);
		paymentInfoModel.setDuplicate(Boolean.FALSE);
		paymentInfoModel.setSaved(Boolean.TRUE);
		paymentInfoModel.setUser(currentUser);
		paymentInfoModel.setPaymentInfo(orderComments);
		paymentInfoModel.setOrderHistoryNotes(bankDetails);
		paymentInfoModel.setPaymentProvider(currentPayment);
		paymentInfoModel.setPaymentGatewayStatus(transactionStatus);
		cartModel.setPaymentInfo(paymentInfoModel);
		paymentInfoModel.setCode("");
		
		PaymentTransactionEntryModel orderTransactionEntry = null;
		final List<PaymentTransactionEntryModel> paymentTransactionEntries = new ArrayList<>();
		orderTransactionEntry = createTransactionEntry(transactionID,
											cartModel, orderAmountCent, backendTransactionComments, currency);
		paymentTransactionEntries.add(orderTransactionEntry);

		// Initiate/ Update PaymentTransactionModel
		PaymentTransactionModel paymentTransactionModel = new PaymentTransactionModel();
		paymentTransactionModel.setPaymentProvider(currentPayment);
		paymentTransactionModel.setRequestId(transactionID);
		paymentTransactionModel.setEntries(paymentTransactionEntries);
		paymentTransactionModel.setOrder(cartModel);
		paymentTransactionModel.setInfo(paymentInfoModel);

		// Update the OrderModel
		cartModel.setPaymentTransactions(Arrays.asList(paymentTransactionModel));
		
		beforePlaceOrder(cartModel);
		final OrderModel orderModel = placeOrder(cartModel);
		String orderNumber = orderModel.getCode();
		
		updateOrderStatus(orderNumber, paymentInfoModel);

        PaymentModeModel paymentModeModel = paymentModeService.getPaymentModeForCode(currentPayment);

        if ("novalnetCreditCard".equals(currentPayment)) {
            NovalnetCreditCardPaymentModeModel novalnetPaymentMethod = (NovalnetCreditCardPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetDirectDebitSepa".equals(currentPayment)) {

            NovalnetDirectDebitSepaPaymentModeModel novalnetPaymentMethod = (NovalnetDirectDebitSepaPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetGuaranteedInvoice".equals(currentPayment)) {
            NovalnetGuaranteedInvoicePaymentModeModel novalnetPaymentMethod = (NovalnetGuaranteedInvoicePaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetGuaranteedDirectDebitSepa".equals(currentPayment)) {
            NovalnetGuaranteedDirectDebitSepaPaymentModeModel novalnetPaymentMethod = (NovalnetGuaranteedDirectDebitSepaPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetPayPal".equals(currentPayment)) {
            NovalnetPayPalPaymentModeModel novalnetPaymentMethod = (NovalnetPayPalPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetInvoice".equals(currentPayment)) {
            NovalnetInvoicePaymentModeModel novalnetPaymentMethod = (NovalnetInvoicePaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetPrepayment".equals(currentPayment)) {
            NovalnetPrepaymentPaymentModeModel novalnetPaymentMethod = (NovalnetPrepaymentPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);

        } else if ("novalnetBarzahlen".equals(currentPayment)) {
            NovalnetBarzahlenPaymentModeModel novalnetPaymentMethod = (NovalnetBarzahlenPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetInstantBankTransfer".equals(currentPayment)) {
            NovalnetInstantBankTransferPaymentModeModel novalnetPaymentMethod = (NovalnetInstantBankTransferPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetOnlineBankTransfer".equals(currentPayment)) {
            NovalnetOnlineBankTransferPaymentModeModel novalnetPaymentMethod = (NovalnetOnlineBankTransferPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetMultibanco".equals(currentPayment)) {
            NovalnetMultibancoPaymentModeModel novalnetPaymentMethod = (NovalnetMultibancoPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetBancontact".equals(currentPayment)) {
            NovalnetBancontactPaymentModeModel novalnetPaymentMethod = (NovalnetBancontactPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetPostFinanceCard".equals(currentPayment)) {
            NovalnetPostFinanceCardPaymentModeModel novalnetPaymentMethod = (NovalnetPostFinanceCardPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetPostFinance".equals(currentPayment)) {
            NovalnetPostFinancePaymentModeModel novalnetPaymentMethod = (NovalnetPostFinancePaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetIdeal".equals(currentPayment)) {
            NovalnetIdealPaymentModeModel novalnetPaymentMethod = (NovalnetIdealPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetEps".equals(currentPayment)) {
            NovalnetEpsPaymentModeModel novalnetPaymentMethod = (NovalnetEpsPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetGiropay".equals(currentPayment)) {
            NovalnetGiropayPaymentModeModel novalnetPaymentMethod = (NovalnetGiropayPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        } else if ("novalnetPrzelewy24".equals(currentPayment)) {
            NovalnetPrzelewy24PaymentModeModel novalnetPaymentMethod = (NovalnetPrzelewy24PaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);
        }
        
        paymentInfoModel.setPaymentInfo(orderComments);
		paymentInfoModel.setPaymentProvider(currentPayment);
		paymentInfoModel.setPaymentGatewayStatus(transactionStatus);;
		paymentInfoModel.setOrderHistoryNotes(bankDetails);
		orderModel.setStatusInfo(backendTransactionComments);
		paymentInfoModel.setCode(orderNumber);
        
        this.getModelService().saveAll(paymentInfoModel, cartModel, billingAddress);
        
        OrderHistoryEntryModel orderEntry = this.getModelService().create(OrderHistoryEntryModel.class);
		orderEntry.setTimestamp(new Date());
		orderEntry.setOrder(orderModel);
		orderEntry.setDescription(backendTransactionComments);
		orderModel.setPaymentInfo(paymentInfoModel);

        int orderPaidAmount = 0;
        String[] bankPayments = {"novalnetInvoice", "novalnetPrepayment", "novalnetBarzahlen", "novalnetGuaranteedDirectDebitSepa", "novalnetGuaranteedInvoice"};
        boolean isInvoicePrepayment = Arrays.asList(bankPayments).contains(currentPayment);

        String[] pendingStatusCode = {"PENDING"};

        // Check for payment pending payments
        if (isInvoicePrepayment || Arrays.asList(pendingStatusCode).contains(transactionStatus)) {
            orderPaidAmount = 0;
        } else {
            orderPaidAmount = orderAmountCent;
        }
        
        this.getModelService().saveAll(orderModel, orderEntry);

		afterPlaceOrder(cartModel, orderModel);

        long callbackInfoTid = Long.parseLong(transactionID);

        NovalnetCallbackInfoModel novalnetCallbackInfo = new NovalnetCallbackInfoModel();
        novalnetCallbackInfo.setPaymentType(currentPayment);
        novalnetCallbackInfo.setOrderAmount(orderAmountCent);
        novalnetCallbackInfo.setCallbackTid(callbackInfoTid);
        novalnetCallbackInfo.setOrginalTid(callbackInfoTid);
        novalnetCallbackInfo.setPaidAmount(orderPaidAmount);
        novalnetCallbackInfo.setOrderNo(orderNumber);
        this.getModelService().save(novalnetCallbackInfo);

        // Save the updated models

        return getOrderConverter().convert(orderModel);

    }


    public String getPaymentName(String currentPayment) {

        String paymentName = "";

        PaymentModeModel paymentModeModel = paymentModeService.getPaymentModeForCode(currentPayment);

        if ("novalnetDirectDebitSepa".equals(currentPayment)) {
            NovalnetDirectDebitSepaPaymentModeModel novalnetPaymentMethod = (NovalnetDirectDebitSepaPaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();

        } else if ("novalnetGuaranteedDirectDebitSepa".equals(currentPayment)) {
            NovalnetGuaranteedDirectDebitSepaPaymentModeModel novalnetPaymentMethod = (NovalnetGuaranteedDirectDebitSepaPaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();
            
        } else if ("novalnetPayPal".equals(currentPayment)) {
            NovalnetPayPalPaymentModeModel novalnetPaymentMethod = (NovalnetPayPalPaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();

        } else if ("novalnetCreditCard".equals(currentPayment)) {
            NovalnetCreditCardPaymentModeModel novalnetPaymentMethod = (NovalnetCreditCardPaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();
            
        } else if ("novalnetInvoice".equals(currentPayment)) {
            NovalnetInvoicePaymentModeModel novalnetPaymentMethod = (NovalnetInvoicePaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();
            
        } else if ("novalnetPrepayment".equals(currentPayment)) {
            NovalnetPrepaymentPaymentModeModel novalnetPaymentMethod = (NovalnetPrepaymentPaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();
            
        } else if ("novalnetMultibanco".equals(currentPayment)) {
            NovalnetMultibancoPaymentModeModel novalnetPaymentMethod = (NovalnetMultibancoPaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();
            
        } else if ("novalnetGuaranteedInvoice".equals(currentPayment)) {
            NovalnetGuaranteedInvoicePaymentModeModel novalnetPaymentMethod = (NovalnetGuaranteedInvoicePaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();
            
            
        } else if ("novalnetBarzahlen".equals(currentPayment)) {
            NovalnetBarzahlenPaymentModeModel novalnetPaymentMethod = (NovalnetBarzahlenPaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();
           
        } else if ("novalnetInstantBankTransfer".equals(currentPayment)) {
            NovalnetInstantBankTransferPaymentModeModel novalnetPaymentMethod = (NovalnetInstantBankTransferPaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();
           
        } else if ("novalnetOnlineBankTransfer".equals(currentPayment)) {
            NovalnetOnlineBankTransferPaymentModeModel novalnetPaymentMethod = (NovalnetOnlineBankTransferPaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();

        } else if ("novalnetBancontact".equals(currentPayment)) {
            NovalnetBancontactPaymentModeModel novalnetPaymentMethod = (NovalnetBancontactPaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();

        } else if ("novalnetIdeal".equals(currentPayment)) {
            NovalnetIdealPaymentModeModel novalnetPaymentMethod = (NovalnetIdealPaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();

        } else if ("novalnetEps".equals(currentPayment)) {
            NovalnetEpsPaymentModeModel novalnetPaymentMethod = (NovalnetEpsPaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();
            

        } else if ("novalnetGiropay".equals(currentPayment)) {
            NovalnetGiropayPaymentModeModel novalnetPaymentMethod = (NovalnetGiropayPaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();
            
        } else if ("novalnetPostFinance".equals(currentPayment)) {
            NovalnetPostFinancePaymentModeModel novalnetPaymentMethod = (NovalnetPostFinancePaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();

        } else if ("novalnetPostFinanceCard".equals(currentPayment)) {
            NovalnetPostFinanceCardPaymentModeModel novalnetPaymentMethod = (NovalnetPostFinanceCardPaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();

        } else if ("novalnetPrzelewy24".equals(currentPayment)) {
            NovalnetPrzelewy24PaymentModeModel novalnetPaymentMethod = (NovalnetPrzelewy24PaymentModeModel) paymentModeModel;
            paymentName = novalnetPaymentMethod.getName();
            
        }

        return paymentName;
    }
    
    /**
     * update the callback order status
     *
     * @param orderCode Order code of the order
     * @param paymentMethod name of the payment method
     */
    public void updateCallbackOrderStatus(String orderCode, String paymentMethod)
	{
		List<OrderModel> orderInfoModel = getOrderInfoModel(orderCode);

		// Update OrderHistoryEntries
		OrderModel orderModel = this.getModelService().get(orderInfoModel.get(0).getPk());
		PaymentModeModel paymentModeModel = paymentModeService.getPaymentModeForCode(paymentMethod);
		
		if("novalnetInvoice".equals(paymentMethod)) 
		{
			final NovalnetInvoicePaymentModeModel novalnetPaymentMethod = (NovalnetInvoicePaymentModeModel) paymentModeModel;
			orderModel.setStatus(novalnetPaymentMethod.getNovalnetCallbackOrderStatus());
		}
		else if("novalnetMultibanco".equals(paymentMethod)) 
		{
			final NovalnetMultibancoPaymentModeModel novalnetPaymentMethod = (NovalnetMultibancoPaymentModeModel) paymentModeModel;
			orderModel.setStatus(novalnetPaymentMethod.getNovalnetCallbackOrderStatus());
		}
		else if("novalnetPrepayment".equals(paymentMethod)) 
		{
			final NovalnetPrepaymentPaymentModeModel novalnetPaymentMethod = (NovalnetPrepaymentPaymentModeModel) paymentModeModel;
			orderModel.setStatus(novalnetPaymentMethod.getNovalnetCallbackOrderStatus());
		}
		else if("novalnetBarzahlen".equals(paymentMethod)) 
		{
			final NovalnetBarzahlenPaymentModeModel novalnetPaymentMethod = (NovalnetBarzahlenPaymentModeModel) paymentModeModel;
			orderModel.setStatus(novalnetPaymentMethod.getNovalnetCallbackOrderStatus());
		}
		else if("novalnetPayPal".equals(paymentMethod)) 
		{
			final NovalnetPayPalPaymentModeModel novalnetPaymentMethod = (NovalnetPayPalPaymentModeModel) paymentModeModel;
			orderModel.setStatus(novalnetPaymentMethod.getNovalnetOrderSuccessStatus());
		}
		else if("novalnetPrzelewy24".equals(paymentMethod)) 
		{
			final NovalnetPrzelewy24PaymentModeModel novalnetPaymentMethod = (NovalnetPrzelewy24PaymentModeModel) paymentModeModel;
			orderModel.setStatus(novalnetPaymentMethod.getNovalnetOrderSuccessStatus());
		}
		
		
		orderModel.setPaymentStatus(PaymentStatus.PAID);

		this.getModelService().save(orderModel);
	}

    /**
     * Get Basestore Model
     *
     * @return Basestore configuration
     */
    public BaseStoreModel getBaseStoreModel() {
        return getBaseStoreService().getCurrentBaseStore();
    }

	 /**
     * Create transaction entry
     *
     * @param requestId request id 
     * @param cartModel cart details
     * @param amount order amount
     * @param backendTransactionComments transaction comments
     * @param currencyCode order currency code
     * @return paymentTransactionEntry
     */
    public PaymentTransactionEntryModel createTransactionEntry(final String requestId, final CartModel cartModel, final int amount, String backendTransactionComments, String currencyCode) {
        final PaymentTransactionEntryModel paymentTransactionEntry = getModelService().create(PaymentTransactionEntryModel.class);
        paymentTransactionEntry.setRequestId(requestId);
        paymentTransactionEntry.setType(PaymentTransactionType.AUTHORIZATION);
        paymentTransactionEntry.setTransactionStatus(TransactionStatus.ACCEPTED.name());
        paymentTransactionEntry.setTransactionStatusDetails(backendTransactionComments);
        paymentTransactionEntry.setCode(cartModel.getCode());

        final CurrencyModel currency = getCurrencyForIsoCode(currencyCode);
        paymentTransactionEntry.setCurrency(currency);

        final BigDecimal transactionAmount = BigDecimal.valueOf(amount / 100);
        paymentTransactionEntry.setAmount(transactionAmount);
        paymentTransactionEntry.setTime(new Date());

        return paymentTransactionEntry;
    }

	/**
     * Get Current user
     *
     * @return currentUser
     */
    public UserModel getCurrentUser() {
        final UserModel currentUser = getCurrentUserForCheckout();

        return currentUser;
    }
	
	/**
     * Get Billing address
     *
     * @return billingAddress
     */
    public AddressModel getBillingAddress() {
        AddressModel billingAddress = this.getModelService().create(AddressModel.class);
        return billingAddress;
    }

	/**
     * Get Billing address
     *
     * @return billingAddress
     */
    public CartModel getNovalnetCheckoutCart() {
        final CartModel cartModel = getCart();
        return cartModel;
    }

    /**
     * Get stored payment info model
     *
     * @param customerNo Customer Id of the transaction
     * @param paymentType payment type
     * @return SearchResult
     */
    public List<NovalnetPaymentRefInfoModel> getPaymentRefInfo(String customerNo, String paymentType) {
        // Initialize StringBuilder
        long customerId = Long.parseLong(customerNo);
        StringBuilder countQuery = new StringBuilder();
        countQuery.append("SELECT {pk} from {" + NovalnetPaymentRefInfoModel._TYPECODE + "} where {" + NovalnetPaymentRefInfoModel.CUSTOMERNO
                + "} = ?customerNo AND {" + NovalnetPaymentRefInfoModel.PAYMENTTYPE + "} = ?paymentType");
        FlexibleSearchQuery executeCountQuery = new FlexibleSearchQuery(countQuery.toString());
        executeCountQuery.addQueryParameter("paymentType", paymentType);
        executeCountQuery.addQueryParameter("customerNo", customerId);
        SearchResult<NovalnetPaymentRefInfoModel> countResult = getFlexibleSearchService().search(executeCountQuery);
        
        final List<NovalnetPaymentRefInfoModel> countPaymentInfo = countResult.getResult(); 
        
        StringBuilder query = new StringBuilder();

        // Select query for fetch NovalnetPaymentRefInfoModel
        query.append("SELECT {pk} from {" + NovalnetPaymentRefInfoModel._TYPECODE + "} where {" + NovalnetPaymentRefInfoModel.CUSTOMERNO
                + "} = ?customerNo AND {" + NovalnetPaymentRefInfoModel.PAYMENTTYPE + "} = ?paymentType ORDER BY {creationtime} DESC");
                
        FlexibleSearchQuery executeQuery = new FlexibleSearchQuery(query.toString());

        // Add query parameter
        executeQuery.addQueryParameter("customerNo", customerId);
        executeQuery.addQueryParameter("paymentType", paymentType);

        // Execute query
        SearchResult<NovalnetPaymentRefInfoModel> result = getFlexibleSearchService().search(executeQuery);

        final List<NovalnetPaymentRefInfoModel> paymentInfo = result.getResult();
        return paymentInfo;
    }


    /**
     * @return the flexibleSearchService
     */
    @SuppressWarnings("javadoc")
    public FlexibleSearchService getFlexibleSearchService() {
        return flexibleSearchService;
    }

    private CurrencyModel getCurrencyForIsoCode(final String currencyIsoCode) {
        CurrencyModel currencyModel = new CurrencyModel();
        currencyModel.setIsocode(currencyIsoCode);
        currencyModel = flexibleSearchService.getModelByExample(currencyModel);
        return currencyModel;
    }

    /**
     * @param flexibleSearchService the flexibleSearchService to set
     */
    @SuppressWarnings("javadoc")
    public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService) {
        this.flexibleSearchService = flexibleSearchService;
    }
	
	/**
     * Update Payment info details in database
     *
     * @param orderReference order details
     * @param tidStatus status of the transacction 
     */
    public void updatePaymentInfo(List<NovalnetPaymentInfoModel> orderReference, String tidStatus) {
        NovalnetPaymentInfoModel paymentInfoModel = this.getModelService().get(orderReference.get(0).getPk());

        // Update Callback TID
        paymentInfoModel.setPaymentGatewayStatus(tidStatus);

        // Save the updated model
        this.getModelService().save(paymentInfoModel);
    }

    /**
     * Update Order comments
     *
     * @param comments  Formed comments
     * @param orderCode Order code of the order
     * @param transactionStatus transaction status for the order
     */
    public void updateCallbackComments(String comments, String orderCode, String transactionStatus) {
        List<NovalnetPaymentInfoModel> paymentInfo = getNovalnetPaymentInfo(orderCode);

        // Update NovalnetPaymentInfo Order entry notes
        NovalnetPaymentInfoModel paymentInfoModel = this.getModelService().get(paymentInfo.get(0).getPk());
        String previousComments = paymentInfoModel.getOrderHistoryNotes();
        paymentInfoModel.setOrderHistoryNotes(previousComments + "<br><br>" + comments);
        paymentInfoModel.setPaymentGatewayStatus(transactionStatus);
        List<OrderModel> orderInfoModel = getOrderInfoModel(orderCode);

        // Update OrderHistoryEntries
        OrderModel orderModel = this.getModelService().get(orderInfoModel.get(0).getPk());
        OrderHistoryEntryModel orderEntry = this.getModelService().create(OrderHistoryEntryModel.class);
        orderEntry.setTimestamp(new Date());
        orderEntry.setOrder(orderModel);
        orderEntry.setDescription(comments);

        // Save the updated models
        this.getModelService().saveAll(paymentInfoModel, orderEntry);
    }


    /**
     * Get Novalnet payment info model
     *
     * @param orderCode Order code of the order
     * @return SearchResult
     */
    public List<NovalnetPaymentInfoModel> getNovalnetPaymentInfo(String orderCode) {

        // Initialize StringBuilder
        StringBuilder query = new StringBuilder();

        // Select query for fetch NovalnetPaymentInfoModel
        query.append("SELECT {pk} from {PaymentInfo} where {" + PaymentInfoModel.CODE
                + "} = ?code AND {" + PaymentInfoModel.DUPLICATE + "} = ?duplicate");
        FlexibleSearchQuery executeQuery = new FlexibleSearchQuery(query.toString());

        // Add query parameter
        executeQuery.addQueryParameter("code", orderCode);
        executeQuery.addQueryParameter("duplicate", Boolean.FALSE);

        // Execute query
        SearchResult<NovalnetPaymentInfoModel> result = getFlexibleSearchService().search(executeQuery);
        return result.getResult();

    }

    /**
     * Is Guest User.
     *
     * @return Boolean
     */
    public Boolean isGuestUser() {
        final CartModel cart = cartService.getSessionCart();
        final UserModel user = cart.getUser();
        return (user instanceof CustomerModel && ((CustomerModel) user).getType() == CustomerType.GUEST) ? true : false;
    }

    /**
     * Get callback info model
     *
     * @param transactionId Transaction ID of the order
     * @return SearchResult
     */
    public List<NovalnetCallbackInfoModel> getCallbackInfo(String transactionId) {
        // Initialize StringBuilder
        StringBuilder query = new StringBuilder();

        // Select query for fetch NovalnetCallbackInfoModel
        query.append("SELECT {pk} from {" + NovalnetCallbackInfoModel._TYPECODE + "} where {" + NovalnetCallbackInfoModel.ORGINALTID
                + "} = ?transctionId");
        FlexibleSearchQuery executeQuery = new FlexibleSearchQuery(query.toString());

        // Add query parameter
        executeQuery.addQueryParameter("transctionId", transactionId);

        // Execute query
        SearchResult<NovalnetCallbackInfoModel> result = getFlexibleSearchService().search(executeQuery);
        return result.getResult();
    }

    /**
     * Get guest Email adsress
     *
     * @return Email address
     */
    public String getGuestEmail() {
        final CartModel cart = cartService.getSessionCart();
        final UserModel user = cart.getUser();
        return (user instanceof CustomerModel && ((CustomerModel) user).getType() == CustomerType.GUEST) ? user.getUid().substring(user.getUid().indexOf('|') + 1) : null;
    }


    /**
     * Update callback info model
     *
     * @param callbackTid     Transaction Id of the executed callback
     * @param orderReference  Order reference list
     * @param orderPaidAmount Total paid amount
     */
    public void updateCallbackInfo(long callbackTid, List<NovalnetCallbackInfoModel> orderReference, int orderPaidAmount) {
        NovalnetCallbackInfoModel callbackInfoModel = this.getModelService().get(orderReference.get(0).getPk());

        // Update Callback TID
        callbackInfoModel.setCallbackTid(callbackTid);

        // Update Paid amount
        callbackInfoModel.setPaidAmount(orderPaidAmount);

        // Save the updated model
        this.getModelService().save(callbackInfoModel);
    }

    /**
     * Update OrderStatus of the order
     *
     * @param orderCode Order code of the order
     */
    public void updatePartPaidStatus(String orderCode) {
        List<OrderModel> orderInfoModel = getOrderInfoModel(orderCode);

        // Update Part paid status
        OrderModel orderModel = this.getModelService().get(orderInfoModel.get(0).getPk());
        orderModel.setPaymentStatus(PaymentStatus.PARTPAID);

        this.getModelService().save(orderModel);
    }
}
