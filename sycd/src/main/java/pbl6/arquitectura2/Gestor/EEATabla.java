package pbl6.arquitectura2.Gestor;


import java.util.HashMap;
import java.util.Map;

public class EEATabla {

    public static final double CO2_DEFAULT_G_KM = 118.0;
    public static final double CO2_BUS_G_KM     = 80.0;
    public static final double CO2_TREN_G_KM    = 40.0;

    private static final Map<String, Double> TABLA = new HashMap<>();

    static {
        TABLA.put("VOLKSWAGEN|GOLF",120.0); TABLA.put("VOLKSWAGEN|POLO",110.0);
        TABLA.put("VOLKSWAGEN|PASSAT",135.0); TABLA.put("VOLKSWAGEN|TIGUAN",148.0);
        TABLA.put("VOLKSWAGEN|ID3",0.0); TABLA.put("VOLKSWAGEN|ID4",0.0);
        TABLA.put("SEAT|IBIZA",108.0); TABLA.put("SEAT|LEON",118.0);
        TABLA.put("SEAT|ATECA",142.0); TABLA.put("SEAT|ARONA",116.0);
        TABLA.put("RENAULT|CLIO",105.0); TABLA.put("RENAULT|MEGANE",120.0);
        TABLA.put("RENAULT|KADJAR",140.0); TABLA.put("RENAULT|ZOE",0.0);
        TABLA.put("RENAULT|CAPTUR",128.0);
        TABLA.put("TOYOTA|YARIS",100.0); TABLA.put("TOYOTA|COROLLA",95.0);
        TABLA.put("TOYOTA|RAV4",130.0); TABLA.put("TOYOTA|PRIUS",92.0);
        TABLA.put("TOYOTA|AYGO",108.0);
        TABLA.put("FORD|FIESTA",113.0); TABLA.put("FORD|FOCUS",125.0);
        TABLA.put("FORD|KUGA",145.0); TABLA.put("FORD|PUMA",118.0);
        TABLA.put("FORD|MUSTANG_MACH_E",0.0);
        TABLA.put("BMW|SERIE1",132.0); TABLA.put("BMW|SERIE3",138.0);
        TABLA.put("BMW|X1",148.0); TABLA.put("BMW|I3",0.0);
        TABLA.put("MERCEDES|CLASE_A",135.0); TABLA.put("MERCEDES|CLASE_C",145.0);
        TABLA.put("MERCEDES|GLA",155.0); TABLA.put("MERCEDES|EQC",0.0);
        TABLA.put("OPEL|CORSA",112.0); TABLA.put("OPEL|ASTRA",128.0);
        TABLA.put("OPEL|CROSSLAND",138.0);
        TABLA.put("HYUNDAI|I20",110.0); TABLA.put("HYUNDAI|I30",120.0);
        TABLA.put("HYUNDAI|TUCSON",142.0); TABLA.put("HYUNDAI|IONIQ5",0.0);
        TABLA.put("HYUNDAI|KONA",125.0);
        TABLA.put("KIA|RIO",112.0); TABLA.put("KIA|CEED",122.0);
        TABLA.put("KIA|SPORTAGE",145.0); TABLA.put("KIA|EV6",0.0);
        TABLA.put("KIA|NIRO",95.0);
        TABLA.put("PEUGEOT|208",112.0); TABLA.put("PEUGEOT|308",128.0);
        TABLA.put("PEUGEOT|3008",145.0); TABLA.put("PEUGEOT|E208",0.0);
        TABLA.put("CITROEN|C3",112.0); TABLA.put("CITROEN|C4",128.0);
        TABLA.put("CITROEN|E_C4",0.0);
        TABLA.put("SKODA|FABIA",110.0); TABLA.put("SKODA|OCTAVIA",125.0);
        TABLA.put("SKODA|KAROQ",140.0); TABLA.put("SKODA|ENYAQ",0.0);
        TABLA.put("TESLA|MODEL3",0.0); TABLA.put("TESLA|MODELY",0.0);
        TABLA.put("TESLA|MODELS",0.0);
        TABLA.put("VOLKSWAGEN|DEFAULT",128.0); TABLA.put("SEAT|DEFAULT",121.0);
        TABLA.put("RENAULT|DEFAULT",118.0); TABLA.put("TOYOTA|DEFAULT",105.0);
        TABLA.put("FORD|DEFAULT",125.0); TABLA.put("BMW|DEFAULT",138.0);
        TABLA.put("MERCEDES|DEFAULT",145.0); TABLA.put("OPEL|DEFAULT",126.0);
        TABLA.put("HYUNDAI|DEFAULT",122.0); TABLA.put("KIA|DEFAULT",119.0);
        TABLA.put("PEUGEOT|DEFAULT",128.0); TABLA.put("CITROEN|DEFAULT",120.0);
        TABLA.put("SKODA|DEFAULT",125.0); TABLA.put("TESLA|DEFAULT",0.0);
    }

    public static double obtenerFactor(String marca, String modelo) {
        String m = normalizar(marca), mod = normalizar(modelo);
        String clave = m + "|" + mod;
        if (TABLA.containsKey(clave)) return TABLA.get(clave);
        String claveMarca = m + "|DEFAULT";
        if (TABLA.containsKey(claveMarca)) {
            System.out.printf("[EEA] '%s %s' no encontrado → media marca: %.1f g/km%n",
                    marca, modelo, TABLA.get(claveMarca));
            return TABLA.get(claveMarca);
        }
        return CO2_DEFAULT_G_KM;
    }

    private static String normalizar(String s) {
        return s.toUpperCase().trim()
                .replace(" ","_").replace("-","_")
                .replace("Á","A").replace("É","E").replace("Í","I")
                .replace("Ó","O").replace("Ú","U").replace("Ñ","N");
    }
}
