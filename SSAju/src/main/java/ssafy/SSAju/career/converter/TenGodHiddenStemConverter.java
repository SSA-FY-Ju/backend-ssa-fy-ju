package ssafy.SSAju.career.converter;

import jakarta.persistence.Converter;
import ssafy.SSAju.career.domain.TenGodHiddenStemAnalysis;

@Converter
public class TenGodHiddenStemConverter extends AbstractJsonConverter<TenGodHiddenStemAnalysis> {

    public TenGodHiddenStemConverter() {
        super(TenGodHiddenStemAnalysis.class, "TenGodHiddenStemAnalysis");
    }
}
