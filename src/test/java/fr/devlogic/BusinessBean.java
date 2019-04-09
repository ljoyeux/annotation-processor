package fr.devlogic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;

@Component
//@Transactional
public class BusinessBean {

    @Autowired
    private MyRepo myRepo;
}
