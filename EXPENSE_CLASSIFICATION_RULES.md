# Expense Classification Rules

## Overview

The AI Receipt Processing system uses a rule-based classifier (`ExpenseCategoryClassifier`) to automatically categorize expenses based on merchant name, transaction amount, and description text.

## Supported Categories

The system supports the following expense categories:

1. **fuel** - Gas stations and fuel purchases
2. **insurance** - Auto insurance premiums
3. **maintenance** - Auto repairs, oil changes, tire service
4. **parking** - Parking lots, meters, and garages
5. **toll** - Highway tolls and transponder fees
6. **carwash** - Car wash and detailing services
7. **mobile** - Mobile phone and telecom services
8. **legal** - Legal fees and services
9. **supplies** - Office supplies and equipment
10. **others** - Uncategorized expenses (default)

## Classification Logic

### Rule Matching

Each category has a rule definition containing:

- **Merchant Keywords**: List of merchant names and keywords to match
- **Description Keywords**: Optional secondary keywords to match in description text
- **Max Amount**: Optional typical maximum amount for the category
- **Base Confidence**: Confidence score (0-100) if matched

### Scoring Algorithm

1. **Primary Match (Merchant)**: If merchant name contains any keyword → assign base confidence
2. **Secondary Match (Description)**: If no merchant match, check description → assign (base confidence - 20)
3. **Amount Check**: If amount exceeds typical max → reduce confidence by 30 points
4. **Threshold**: Only accept classification if confidence ≥ 50%, otherwise default to "others"

### Examples

#### Example 1: Shell Gas Station
```
Merchant: "Shell Canada #1234"
Amount: $65.00
Result: TYPE_FUEL (confidence: 90)
Reason: Matches keyword "shell", amount within typical range
```

#### Example 2: Parking Meter
```
Merchant: "City Parking Meter 5678"
Amount: $12.50
Result: TYPE_PARKING (confidence: 85)
Reason: Matches keyword "parking", amount < $200 typical max
```

#### Example 3: Unknown Merchant
```
Merchant: "ABC Store"
Amount: $50.00
Result: TYPE_OTHERS (confidence: 0)
Reason: No keyword matches
```

#### Example 4: Ambiguous - Reduced Confidence
```
Merchant: "Parking Authority"
Amount: $500.00
Result: TYPE_OTHERS (confidence: 55 → reduced to 25 due to high amount)
Reason: Amount exceeds typical parking max ($200), confidence dropped below threshold
```

## Current Rules

### Fuel (TYPE_FUEL)
- **Keywords**: shell, esso, petro-canada, ultramar, husky, mobil, chevron, sunoco, fas gas, co-op, costco gas, canadian tire gas, bp, exxon, total, gulf, valero
- **Max Amount**: None
- **Confidence**: 90

### Parking (TYPE_PARKING)
- **Keywords**: parking, park, impark, easypark, parkade, parking lot, parking meter, parkplus, honk, parkmobile, paybyphone, indigo, valet
- **Max Amount**: $200
- **Confidence**: 85

### Toll (TYPE_TOLL)
- **Keywords**: toll, 407 etr, 407etr, transponder, e-zpass, ezpass, highway toll, bridge toll, tunnel toll
- **Max Amount**: $500
- **Confidence**: 95

### Car Wash (TYPE_CARWASH)
- **Keywords**: car wash, carwash, auto wash, autowash, car detailing, detailing, wash bay, touchless wash
- **Max Amount**: $150
- **Confidence**: 90

### Maintenance (TYPE_MAINTENANCE)
- **Keywords**: canadian tire, mr. lube, jiffy lube, midas, speedy, kal tire, fountain tire, ok tire, active green, meineke, auto repair, mechanic, garage, oil change, tire, brake, muffler, transmission, tune-up, alignment, battery, auto parts, napa, part source, autozone
- **Description Keywords**: oil, tire, brake, filter, service, repair, maintenance, inspection, diagnostic, parts
- **Max Amount**: None
- **Confidence**: 85

### Insurance (TYPE_INSURANCE)
- **Keywords**: insurance, intact, desjardins, td insurance, aviva, belairdirect, cooperators, wawanesa, allstate, state farm, geico, progressive, auto insurance, car insurance
- **Description Keywords**: insurance, premium, policy, coverage
- **Max Amount**: $10,000
- **Confidence**: 95

### Mobile/Telecom (TYPE_MOBILE)
- **Keywords**: rogers, bell, telus, fido, koodo, virgin mobile, freedom mobile, chatr, public mobile, lucky mobile, fizz, verizon, at&t, t-mobile, wireless, cellular, mobile phone
- **Description Keywords**: mobile, wireless, cellular, phone
- **Max Amount**: $500
- **Confidence**: 90

### Legal (TYPE_LEGAL)
- **Keywords**: lawyer, attorney, legal, law firm, legal services, solicitor, barrister, notary, court, tribunal, litigation
- **Description Keywords**: legal, law, attorney, court
- **Max Amount**: None
- **Confidence**: 90

### Supplies (TYPE_SUPPLIES)
- **Keywords**: office depot, staples, grand & toy, costco, walmart, amazon, office supplies, business depot, supply, stationery
- **Description Keywords**: supplies, office, stationery, equipment
- **Max Amount**: None
- **Confidence**: 70 (lower due to high false positive rate)

## Extending the Rules

To add or modify classification rules, edit the `ExpenseCategoryClassifier.java` file:

### Adding a New Category

1. Define the category constant in `Expense.java` (if not already exists)
2. Add a new rule in the `CATEGORY_RULES` static block:

```java
CATEGORY_RULES.put(Expense.TYPE_NEW_CATEGORY, new CategoryRule(
        Expense.TYPE_NEW_CATEGORY,
        Arrays.asList("keyword1", "keyword2", "keyword3"),  // Merchant keywords
        Arrays.asList("desc1", "desc2"),                    // Description keywords (optional)
        new BigDecimal("1000.00"),                          // Max amount (optional)
        85                                                   // Base confidence
));
```

### Adding Keywords to Existing Category

Locate the category in `CATEGORY_RULES` and add keywords to the merchant or description lists.

### Adjusting Confidence Scores

- **High confidence (90-95)**: Very specific keywords, low false positive rate (e.g., toll, insurance)
- **Medium confidence (85)**: Moderately specific, some ambiguity (e.g., parking, fuel)
- **Low confidence (70-80)**: Generic keywords, high false positive rate (e.g., supplies)

## Testing Classification

To test the classifier manually:

```java
ExpenseCategoryClassifier.ClassificationResult result =
    ExpenseCategoryClassifier.classify(
        "Shell #1234",           // merchant
        "Fuel purchase",         // description
        new BigDecimal("65.00")  // amount
    );

System.out.println("Category: " + result.getCategory());
System.out.println("Confidence: " + result.getConfidence());
```

## Performance Considerations

- Classification is performed synchronously during receipt processing
- Uses compiled regex patterns for efficient matching
- Typical processing time: < 5ms per receipt
- No external API calls required

## Future Enhancements

### Potential Improvements (Phase 4+)
1. **Machine Learning Model**: Train ML model on historical data for better accuracy
2. **Azure OpenAI Integration**: Use GPT-4 for complex/ambiguous cases
3. **Custom Rules**: Allow users to define custom classification rules via UI
4. **Learning System**: Automatically improve rules based on user corrections
5. **Context-Aware**: Consider time of day, location, and past patterns

## Support

For questions or issues with expense classification, contact the development team or file an issue in the project repository.
