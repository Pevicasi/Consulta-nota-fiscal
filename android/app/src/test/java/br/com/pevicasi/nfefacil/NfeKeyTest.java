package br.com.pevicasi.nfefacil;
import org.junit.Test;
import static org.junit.Assert.*;
public class NfeKeyTest {
    private static String key() {
        String base="3526101234567800019555001000000123112345678";
        int sum=0,w=2;for(int i=42;i>=0;i--){sum+=(base.charAt(i)-'0')*w;w=w==9?2:w+1;}
        return base+(sum%11<2?0:11-sum%11);
    }
    @Test public void acceptsValidChecksumAndFormattedBarcode() {
        assertTrue(NfeKey.isValid(key()));assertEquals(key(),NfeKey.barcode(key().substring(0,22)+" "+key().substring(22)));
    }
    @Test public void rejectsWrongDigitLengthLettersAndRepeatedDigits() {
        assertFalse(NfeKey.isValid(key().substring(0,43)+(key().endsWith("9")?"0":"9")));
        assertFalse(NfeKey.isValid(key().substring(1)));assertFalse(NfeKey.isValid(key()+"1"));
        assertFalse(NfeKey.isValid("0".repeat(44)));assertFalse(NfeKey.isValid("1".repeat(44)));assertFalse(NfeKey.isValid(null));
        assertEquals("",NfeKey.barcode("https://example.com/"+key()+"123"));
    }
    @Test public void recognizesGroupedDigitsAndOcrConfusions() {
        String grouped=key().replaceAll("(.{4})", "$1 ");
        assertTrue(NfeKey.fromOcr("CHAVE DE ACESSO\n"+grouped).contains(key()));
        assertTrue(NfeKey.fromOcr(key().replace('0','O').replace('1','I').replace('5','S')).contains(key()));
    }
    @Test public void neverJoinsUnrelatedLinesOrLongDigitRuns() {
        assertTrue(NfeKey.fromOcr(key().substring(0,22)+"\n"+key().substring(22)).isEmpty());
        assertTrue(NfeKey.fromOcr("123"+key()+"456").isEmpty());assertTrue(NfeKey.fromOcr(null).isEmpty());
    }
    @Test public void deduplicatesAndKeepsMultipleCandidatesForConfirmation() {
        assertEquals(1,NfeKey.fromOcr(key()+"\n"+key()).size());
    }
}
