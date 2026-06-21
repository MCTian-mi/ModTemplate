import com.myname.mymodid.Result.Ok;
import org.junit.jupiter.api.Test;

public class TestMain {

    @Test
    public void foo() {
        var x = new Ok<>(2);
        IO.println(x.mapOrElse(_ -> 3, i -> i));
    }
}
