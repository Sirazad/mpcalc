package hu.mpcalc.engine;

/**
 * Every message the engine can produce. The engine returns the code and its arguments, never text: the text comes from the
 * message files of the application that uses the engine (the backend: messages.properties and messages_en.properties).
 * The key is the message file key. Arguments are written next to each code ({0}, {1}, ...).
 */
public enum MsgCode {
    // ---- reasons why a value is undefined
    NOT_CALCULATED("engine.reason.notCalculated"),
    MODIFIER_REFUSED("engine.reason.modifierRefused"),
    MOSAIC_REFUSED("engine.reason.mosaicRefused"),
    MODIFIER_ONLY_ADDS_DURATION("engine.reason.modifierOnlyAddsDuration"),
    NO_MOSAIC_SELECTED("engine.reason.noMosaicSelected"),
    TOTAL_MP_UNDEFINED("engine.reason.totalMpUndefined"),
    A_MOSAIC_REFUSED("engine.reason.aMosaicRefused"),
    NO_NON_MODIFIER_MOSAIC("engine.reason.noNonModifierMosaic"),
    DURATION_KINDS_DIFFER("engine.reason.durationKindsDiffer"),
    DURATIONS_DIFFER("engine.reason.durationsDiffer"),
    OPTION_WITHOUT_EXTRA_MP("engine.reason.optionWithoutExtraMp"),                 // {0} option id
    NO_E("engine.reason.noE"),
    POWER_UNDEFINED("engine.reason.powerUndefined"),
    POWER_INFINITE("engine.reason.powerInfinite"),
    DURATION_FROM_WRAPPED("engine.reason.durationFromWrapped"),
    WRAPPER_INNER_E_UNREACHABLE("engine.reason.wrapperInnerEUnreachable"),                 // {0} mosaic name, {1} strength
    STEP_E_UNDEFINED("engine.reason.stepEUndefined"),
    EFFECT_NOT_DEFINED("engine.reason.effectNotDefined"),
    RESISTANCE_NOT_DEFINED("engine.reason.resistanceNotDefined"),
    RESISTANCE_INPUT_MISSING("engine.reason.resistanceInputMissing"),              // {0} input id
    RESISTANCE_NO_PAIR("engine.reason.resistanceNoPair"),                          // {0} chosen value
    CASTING_TIME_NOT_DEFINED("engine.reason.castingTimeNotDefined"),
    MODIFIER_CASTING_TIME_NOT_DEFINED("engine.reason.modifierCastingTimeNotDefined"),
    MODIFIER_DURATION_NOT_DEFINED("engine.reason.modifierDurationNotDefined"),
    MODIFIER_CASTING_TIME_NOT_A_TIME("engine.reason.modifierCastingTimeNotATime"),  // {0} kind (a message)
    MODIFIER_DURATION_NOT_A_TIME("engine.reason.modifierDurationNotATime"),         // {0} kind (a message)
    CASTING_TIME_NOT_A_TIME("engine.reason.castingTimeNotATime"),                    // {0} kind (a message)
    OPTION_CASTING_TIME_NOT_A_TIME("engine.reason.optionCastingTimeNotATime"),       // {0} kind (a message)
    DURATION_PER_EXTRA_MP_NOT_A_TIME("engine.reason.durationPerExtraMpNotATime"),
    DURATION_NOT_DEFINED("engine.reason.durationNotDefined"),
    NO_FORM_CHOSEN("engine.reason.noFormChosen"),
    FORM_DURATION_NOT_DEFINED("engine.reason.formDurationNotDefined"),              // {0} form id
    CANNOT_ADD_TIMES("engine.reason.cannotAddTimes"),                                // {0} kind, {1} kind (messages)
    CANNOT_MULTIPLY_TIME("engine.reason.cannotMultiplyTime"),                        // {0} kind (a message)
    EV_LENGTH_UNKNOWN("engine.reason.evLengthUnknown"),
    EV_COMPARE_UNITS("engine.reason.evCompareUnits"),
    NO_COST_DATA("engine.reason.noCostData"),
    BASE_MP_UNDEFINED("engine.reason.baseMpUndefined"),
    GRID_RATES_UNDEFINED("engine.reason.gridRatesUndefined"),
    COST_PATTERN_UNDEFINED("engine.reason.costPatternUndefined"),
    UNKNOWN_COST_PATTERN("engine.reason.unknownCostPattern"),                        // {0} pattern
    RATE_UNDEFINED("engine.reason.rateUndefined"),
    PER_MASS_NEEDS_ONE_NUMBER("engine.reason.perMassNeedsOneNumber"),
    WEIGHT_UNIT_NOT_G_OR_KG("engine.reason.weightUnitNotGOrKg"),
    ROUNDING_RULE_UNKNOWN("engine.reason.roundingRuleUnknown"),
    RATES_UNDEFINED("engine.reason.ratesUndefined"),
    NO_COMPOSITION_INPUT("engine.reason.noCompositionInput"),
    GROUP_NOT_AVAILABLE_REASON("engine.reason.groupNotAvailable"),
    GROUP_RATE_UNDEFINED("engine.reason.groupRateUndefined"),                        // {0} group
    LOOKUP_DATA_UNDEFINED("engine.reason.lookupDataUndefined"),
    LOOKUP_INPUT_MISSING("engine.reason.lookupInputMissing"),
    TABLE_MP_UNDEFINED("engine.reason.tableMpUndefined"),
    VALUE_OUTSIDE_TABLE("engine.reason.valueOutsideTable"),

    // ---- warnings
    WARN_NOT_WHOLE_MP("engine.warn.notWholeMp"),
    WARN_MIXED_CASTING_TIMES("engine.warn.mixedCastingTimes"),
    WARN_NOT_ENOUGH_MP("engine.warn.notEnoughMp"),

    // ---- errors (the mosaic is refused)
    ERR_STATUS_MISSING("engine.error.statusMissing"),
    ERR_INCOMPLETE_MOSAIC("engine.error.incompleteMosaic"),
    ERR_UNKNOWN_STATUS("engine.error.unknownStatus"),                                // {0} state
    ERR_SCHOOL_MISSING("engine.error.schoolMissing"),
    ERR_TIER_MISSING("engine.error.tierMissing"),
    ERR_WRAPPER_WRONG_MOSAIC("engine.error.wrapperWrongMosaic"),                            // {0} other mosaic, {1} what the wrapper accepts
    ERR_ONE_JELMAGIA_PER_SPELL("engine.error.oneJelmagiaPerSpell"),                         // {0} the Jelmágia mosaic that is already in the spell
    ERR_INPUT_NOT_WHOLE("engine.error.inputNotWhole"),                                       // {0} input id
    ERR_INPUT_BELOW_MIN("engine.error.inputBelowMin"),                                       // {0} input id, {1} smallest value
    ERR_TIER_BELOW("engine.error.tierBelow"),                                        // {0} have, {1} school, {2} needed
    ERR_K_NEGATIVE("engine.error.kNegative"),
    ERR_POWER_NOT_RAISABLE("engine.error.powerNotRaisable"),
    ERR_UNKNOWN_OPTION("engine.error.unknownOption"),                                // {0} option id
    ERR_EXTRA_MP_NEGATIVE("engine.error.extraMpNegative"),
    ERR_EXTRA_MP_NOT_ALLOWED("engine.error.extraMpNotAllowed"),
    ERR_MODIFIER_NOT_WHOLE_SPELL("engine.error.modifierNotWholeSpell"),
    ERR_UNKNOWN_INPUT("engine.error.unknownInput"),                                  // {0} input id
    ERR_INPUT_MISSING("engine.error.inputMissing"),                                  // {0} input id
    ERR_INPUT_VALUE_NOT_ALLOWED("engine.error.inputValueNotAllowed"),                // {0} input id
    ERR_INPUT_NOT_A_NUMBER("engine.error.inputNotANumber"),                          // {0} input id
    ERR_INPUT_NOT_TEXT("engine.error.inputNotText"),                                 // {0} input id
    ERR_INPUT_UNKNOWN_FORM("engine.error.inputUnknownForm"),                         // {0} input id
    ERR_FORM_NO_TIER("engine.error.formNoTier"),                                     // {0} form id
    ERR_FORM_NEEDS_TIER("engine.error.formNeedsTier"),                               // {0} form id, {1} needed, {2} school, {3} have
    ERR_COMPOSITION_EMPTY("engine.error.compositionEmpty"),                          // {0} input id
    ERR_COMPOSITION_PART_INVALID("engine.error.compositionPartInvalid"),             // {0} input id
    ERR_COMPOSITION_DUPLICATE_GROUP("engine.error.compositionDuplicateGroup"),       // {0} input id, {1} group
    ERR_INPUT_UNKNOWN_TYPE("engine.error.inputUnknownType"),                         // {0} input id, {1} type
    ERR_GROUP_NOT_AVAILABLE("engine.error.groupNotAvailable"),                       // {0} group

    // ---- time kinds, used as arguments of other messages
    KIND_TIME("engine.kind.time"),
    KIND_ONE_OFF("engine.kind.oneOff"),
    KIND_PERMANENT("engine.kind.permanent"),
    KIND_FORM_DEPENDENT("engine.kind.formDependent"),
    KIND_UNTIL_BROKEN("engine.kind.untilBroken"),
    KIND_UNDEFINED("engine.kind.undefined"),

    // ---- input could not be parsed (MsgException)
    PARSE_TIME_AMOUNT_NOT_INTEGER("engine.parse.timeAmountNotInteger"),              // {0} unit
    PARSE_TIME_WITHOUT_AMOUNTS("engine.parse.timeWithoutAmounts"),
    PARSE_UNKNOWN_TIME_KIND("engine.parse.unknownTimeKind"),                         // {0} kind
    PARSE_UNKNOWN_TIME_UNIT("engine.parse.unknownTimeUnit"),                         // {0} unit
    PARSE_NEGATIVE_TIME("engine.parse.negativeTime"),                                // {0} unit
    PARSE_BAD_MOSAIC_JSON("engine.parse.badMosaicJson"),                             // {0} technical detail
    PARSE_BAD_FORMS_JSON("engine.parse.badFormsJson");                               // {0} technical detail

    public final String key;

    MsgCode(String key) { this.key = key; }
}
