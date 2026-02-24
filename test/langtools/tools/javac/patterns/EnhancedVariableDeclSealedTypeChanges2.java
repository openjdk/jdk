sealed interface EnhancedVariableDeclSealedTypeChangesIntf
        permits EnhancedVariableDeclSealedTypeChanges.A, EnhancedVariableDeclSealedTypeChangesClass {}

final class EnhancedVariableDeclSealedTypeChangesClass
        implements EnhancedVariableDeclSealedTypeChangesIntf {}
