package io.github.imecuadorian.emitta.srireception.application.port.out;

import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionRequest;
import io.github.imecuadorian.emitta.srireception.application.model.SriReceptionResult;

public interface SriReceptionPort {

    SriReceptionResult submit(
            SriReceptionRequest request
    );
}