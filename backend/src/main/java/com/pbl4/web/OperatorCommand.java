package com.pbl4.web;

import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Local operator access only. No public endpoint can grant itself privileged roles. */
@Component
public class OperatorCommand implements ApplicationRunner {
    private final Database db;
    public OperatorCommand(Database db){this.db=db;}
    @Override public void run(ApplicationArguments args)throws Exception {
        if(!args.containsOption("grant-teacher"))return;
        System.out.println("--grant-teacher is retired. Every active account can create a classroom; ownership defines permissions per classroom. No account was changed.");
    }
}
