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
        grant(args,"grant-admin","ADMIN");
        grant(args,"grant-teacher","TEACHER");
    }
    private void grant(ApplicationArguments args,String option,String role)throws Exception {
        if(!args.containsOption(option))return;
        var values=args.getOptionValues(option);
        if(values==null||values.size()!=1||values.getFirst().isBlank()) throw new IllegalArgumentException("Use --"+option+"=<username>");
        String username=values.getFirst().strip();
        if(!db.setRole(username,role)) throw new IllegalArgumentException("Active account not found: "+username);
        System.out.println("Granted "+role+" to "+username+" via local operator command.");
    }
}
