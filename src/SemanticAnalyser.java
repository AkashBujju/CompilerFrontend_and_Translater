import java.util.List;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;

class FuncNameArgs {
	String name;
	List<String> arg_types;
	String return_type;
}

public class SemanticAnalyser {
	List<Info> infos;	
	SymbolTable symbol_table;
	List<Integer> func_sig_indices;
	List<RangeIndices> quotes_range_indices;
	List<FuncNameArgs> func_name_args;
	ErrorLog error_log;
	String func_iden = "_func@";

	SemanticAnalyser(List<Info> infos, List<RangeIndices> quotes_range_indices) {
		this.infos = infos;
		this.quotes_range_indices = quotes_range_indices;
		func_sig_indices = new ArrayList<>();
		symbol_table = new SymbolTable();
		func_name_args = new ArrayList<>();
		error_log = new ErrorLog();

		int count = 0;
		for(Info i: infos) {
			if(i.info_type == InfoType.FUNCTION) {
				func_sig_indices.add(count);
				FunctionInfo func_info = (FunctionInfo)(i);
				FuncNameArgs func_name_arg = new FuncNameArgs();
				func_name_arg.name = func_info.name;
				func_name_arg.return_type = func_info.return_type;

				List<String> var_arg_types = new ArrayList<>();
				for(VarDeclInfo var: func_info.var_args)
					var_arg_types.add(var.type);

				func_name_arg.arg_types = var_arg_types;
				func_name_args.add(func_name_arg);

				/*
				System.out.println("name: " + func_name_arg.name);
				System.out.println("return_type: " + func_name_arg.return_type);
				System.out.println("arg_types: " + func_name_arg.arg_types);
				System.out.println();
				*/
			}

			count += 1;
		}
	}

	private void init_all_func_scopes() {
		for(Integer i: func_sig_indices)
			init_func_scope(i);
	}

	private void init_func_scope(int index) {
		FunctionInfo func_info = (FunctionInfo)(infos.get(index));
		List<Info> in_infos = func_info.infos;
		String scope_name = func_info.scope_name;
		List<String> scope_names = new ArrayList<>();

		int num_scopes = get_num_scopes(in_infos, 0);
		for(int i = 0; i <= num_scopes; ++i) {
			scope_names.add(scope_name + i);
		}

		FunctionInfo new_func_info = func_info;
		new_func_info.scope_names = scope_names;
		infos.set(index, new_func_info);
	}

	int get_num_scopes(List<Info> i, int count) {
		for(Info info: i) {
			if(info.info_type == InfoType.IF) {
				IfInfo if_info = (IfInfo)(info);
				List<Info> ii = if_info.infos;
				count = get_num_scopes(ii, count + 1);
			}
			else if(info.info_type == InfoType.ELSE_IF) {
				ElseIfInfo if_info = (ElseIfInfo)(info);
				List<Info> ii = if_info.infos;
				count = get_num_scopes(ii, count + 1);
			}
			else if(info.info_type == InfoType.WHILE) {
				WhileInfo if_info = (WhileInfo)(info);
				List<Info> ii = if_info.infos;
				count = get_num_scopes(ii, count + 1);
			}
			else if(info.info_type == InfoType.ELSE) {
				ElseInfo if_info = (ElseInfo)(info);
				List<Info> ii = if_info.infos;
				count = get_num_scopes(ii, count + 1);
			}
		}

		return count;
	}

	public void start() throws FileNotFoundException {
		init_all_func_scopes();

		for(int i = 0; i < infos.size(); ++i) {
			Info info = infos.get(i);	
			int error_res = 0;
			int count_errors = 0;

			if(info.info_type == InfoType.USE)
				error_res = eval_use((UseInfo)(info));
			else if(info.info_type == InfoType.VAR_DECL)
				error_res = eval_var_decl((VarDeclInfo)(info));
			else if(info.info_type == InfoType.FUNCTION)
				error_res = eval_function((FunctionInfo)(info));

			if(error_res == -1)
				count_errors += 1;
			if(count_errors > 2) {
				return;
			}
		}
	}

	// @NotKnown: From where does the filepath start from ??????
	// @NotKnown: From where does the filepath start from ??????
	// @NotKnown: From where does the filepath start from ??????
	// @NotKnown: From where does the filepath start from ??????
	private int eval_use(UseInfo use_info) throws FileNotFoundException {
		String filename = use_info.filename;
		try {
			Scanner s = new Scanner(new BufferedReader(new FileReader(filename)));
		}
		catch(FileNotFoundException e) {
			error_log.push("Could not find file: " + filename + ".", "use \"" + filename+ "\';", use_info.line_number);
			return -1;
		}

		return 0;
	}

	private int eval_function(FunctionInfo func_info) {

		return 0;
	}

	private int eval_var_decl(VarDeclInfo var_decl_info) {
		String raw_value = var_decl_info.raw_value;
		String name = var_decl_info.name;

		// Checking if the variable name is not the name of any type.
		if(symbol_table.type_exists(name)) {
			error_log.push("Name <" + name + "> is not allowed, since it a name of a type.", name, var_decl_info.line_number);
			return -1;
		}

		List<String> split_value = Util.split_with_ops(raw_value);
		List<String> final_exp = new ArrayList<>();

		// System.out.println("split_value: " + split_value);

		int len = split_value.size();
		int num_func_calls = 0;
		for(int i = 0; i < len; ++i) {
			String s = split_value.get(i);
			if(Util.is_operator(s)) {
				final_exp.add(s);
			}
			else {
				boolean if_func_call = Util.if_func_call(s);
				String type = "not_known";
				if(if_func_call) {
					num_func_calls += 1;
					type = get_type_of_func_call(s, var_decl_info.line_number);
					type = func_iden + type;
				}
				else {
					String var_type = symbol_table.get_type(s, "", 0);
					type = get_type_of_exp(s, var_decl_info.line_number);
					// We need to know if 's' is name of a variable, so that we can append '_var@' to it.
					if(!var_type.equals("not_known")) {
						type = "_var@" + type;
					}
				}	

				final_exp.add(type);
			}
		}

		System.out.println("final_exp: " + final_exp + ", var_name: " + name);
		System.out.println("-------------------");

		String final_type = "not_known";
		EvalExp eval_exp = null;

		if(num_func_calls == 1 && final_exp.size() == 1) {
			eval_exp = new EvalExp(final_exp);
			final_type = eval_exp.deduce_final_type(symbol_table, "", 0).type;
		}
		else {
			List<String> postfix_exp = InfixToPostFix.infixToPostFix(final_exp);
			eval_exp = new EvalExp(postfix_exp);
			MsgType msg_type = eval_exp.deduce_final_type_from_types(symbol_table, "", 0);
			if(!msg_type.msg.equals("none")) {
				error_log.push(msg_type.msg, raw_value, var_decl_info.line_number);
				return -1;
			}

			final_type = msg_type.type;
		}

		symbol_table.add(var_decl_info.name, final_type, raw_value);
		System.out.println("FINAL TYPE <" + final_type + ">" + ", name <" + var_decl_info.name + ">");
		System.out.println();

		return  0;
	}

	String get_type_of_exp(String s, int line_number) {
		List<String> in_list = Util.split_with_ops(s);
		List<String> out_list = InfixToPostFix.infixToPostFix(in_list);

		EvalExp eval_exp = new EvalExp(out_list);
		MsgType msg_type = eval_exp.deduce_final_type(symbol_table, "", 0);
		if(msg_type.msg.equals("none"))
			return msg_type.type;
		else {
			error_log.push(msg_type.msg, s, line_number);
		}

		return msg_type.type;
	}

	String get_type_of_func_call(String s, int line_number) {
		/*
		 * Alg: 
		 * 1) Find the inner arguments of the function
		 * 2) Eval all exps for every inner argument
		 * 3) Replace the instances of the exp in the inner_arg with it's type
		 */

		// @Note: This is just one function call, and String 's' is not like add(1, 2) + sub(1, 1), but like add(sub(1, 1), 2).

		List<String> all_args = Util.get_func_args(s);
		String arg = s.substring(s.indexOf('(') + 1, s.lastIndexOf(')'));
		String func_name = s.substring(0, s.indexOf('('));
		/*
			System.out.println("func_name <" + func_name + ">");
			System.out.println("arg: " + arg);
			*/
		boolean func_exists = func_with_num_args_exists(func_name, all_args.size());
		if(!func_exists) {
			error_log.push("Function with name '" + func_name + "' taking '" + all_args.size() + "' arguments dosen't exist.", s, line_number);
			return "not_known";
		}

		// @Incomplete: There can be more than one argument... FIX THAT
		// @Incomplete: There can be more than one argument... FIX THAT
		// @Incomplete: There can be more than one argument... FIX THAT
		// @Incomplete: There can be more than one argument... FIX THAT
		// @Incomplete: There can be more than one argument... FIX THAT
		List<String> exps = Util.get_only_exp(arg, get_all_func_names());
		List<String> funcs = Util.get_all_func_calls(arg);
		String new_arg = arg;

		System.out.println("exps: " + exps);

		for(String exp: exps) {
			int invalid_exp_index = exp.indexOf("error@");
			if(invalid_exp_index != -1) { // Invalid use of unary operator
				error_log.push("Invalid identifier '" + exp.substring(invalid_exp_index + 6) + "' found.",s, line_number);
				return "not_known";
			}

			String type = get_type_of_exp(exp, line_number);
			new_arg = Util.replace_in_str(new_arg, exp, type);
		}

		System.out.println("new_arg: " + new_arg);

		if(!Util.contains_func_call(new_arg)) {
			StringBuffer final_s = new StringBuffer(new_arg);
			final_s.insert(0, func_name + "(");
			final_s.append(")");
			String type = get_type_of_one_func_call(final_s.toString(), line_number);

			return type;
		}

		// @Note: new_arg needs to be enclosed by function name and parenthesis.
		// @Note: new_arg needs to be enclosed by function name and parenthesis.


		StringBuffer tmp_func_call = new StringBuffer(func_name + "(");
		tmp_func_call.append(new_arg);
		tmp_func_call.append(")");
		String evald_func_args = iter_eval_until_no_funcs(tmp_func_call.toString(), line_number);


		StringBuffer final_func_call = new StringBuffer(func_name + "(");
		final_func_call.append(evald_func_args);
		final_func_call.append(")");

		return get_type_of_one_func_call(final_func_call.toString(), line_number);
	}

	// Returns the inner argument of func, with all the function call evaluated.
	String iter_eval_until_no_funcs(String func, int line_number) {
		String final_str =  func;
		String inner_arg = func.substring(func.indexOf('(') + 1, func.lastIndexOf(')'));
		List<String> all_funcs = Util.get_all_func_calls(inner_arg);
		HashMap<String, String> hm = new HashMap<>();

		System.out.println();
		/*
			System.out.println("inner_arg: " + inner_arg);
			System.out.println("all_funcs: " + all_funcs);
			System.out.println();
			*/

		for(String f: all_funcs)
			hm.put(f, "not_known");

		for(int i = all_funcs.size() - 1; i >= 0; --i) {
			String f = all_funcs.get(i);
			String value = hm.get(f);
			if(value == null) {
				hm.put(f, "not_known");
				value = "not_known";
			}

			String type = "not_known";
			if(value.equals("not_known")) {
				if(!Util.if_func_call(f))
					continue;
				type = get_type_of_one_func_call(f, line_number);
				type = func_iden + type;
				// System.out.println("f <" + f + ">, type <" + type + ">");
				hm.put(f, type);

				for(int j = 0; j < all_funcs.size(); ++j) {
					String j_ele = all_funcs.get(j);
					j_ele = Util.replace_in_str(j_ele, f, type);
					all_funcs.set(j, j_ele);

					inner_arg = Util.replace_in_str(inner_arg, f, type);
					if(!Util.contains_func_call(inner_arg)) {
						i = -1;
						break;
					}
				}
			}
		}

		System.out.println("final_inner_arg: " +inner_arg);

		StringBuffer final_func_call = new StringBuffer(func.substring(0, func.indexOf('(')));
		final_func_call.append("(" + inner_arg + ")");

		return get_type_of_one_func_call(final_func_call.toString(), line_number);

		/*
			System.out.println("HM: ");
			Set<String> keyset = hm.keySet();
			Iterator<String> it = keyset.iterator();
			while(it.hasNext()) {
			String key = it.next();
			System.out.println(key + " : " + hm.get(key));
			}
			*/
	}

	// @Note: Here the types of arguments should have already been deduced.
	String get_type_of_one_func_call(String s, int line_number) {
		String in_arg = s.substring(s.indexOf('(') + 1, s.lastIndexOf(')'));
		String name = s.substring(0, s.indexOf('('));
		List<String> arg_types = Util.get_func_args(s);

		// @Note: The arguments may have operations still left to perform on them.
		for(int i = 0; i < arg_types.size(); ++i) {
			String arg = arg_types.get(i);
			List<String> exps = Util.split_with_ops_the_types(arg);
			List<String> postfix = InfixToPostFix.infixToPostFix(exps);
			// System.out.println("postfix: " + postfix);
			EvalExp eval_exp = new EvalExp(postfix);
			MsgType msg_type = eval_exp.deduce_final_type_from_types(symbol_table, "", 0);
		}

		FuncNameArgs func_name_arg = get_func_name_with_args(name, arg_types);
		if(func_name_arg == null) {
			String msg = "Function with name '" + name + "' and argument types " + arg_types + " does not exist";
			error_log.push(msg, s, line_number);
			return "not_known";
		}

		return func_name_arg.return_type;
	}

	FuncNameArgs get_func_name_with_args(String name, List<String> types) {
		for(FuncNameArgs func_name_arg: func_name_args) {
			if(name.equals(func_name_arg.name)) {
				List<String> arg_types = func_name_arg.arg_types;

				if(types.size() == arg_types.size()) {
					boolean all_match = true;
					for(int i = 0; i < types.size(); ++i) {
						String t1 = types.get(i);
						String t2 = arg_types.get(i);
						if(!t1.equals(t2)) {
							all_match = false;
							break;
						}
					}

					if(all_match)
						return func_name_arg;
				}
			}
		}

		return null;
	}

	boolean func_with_num_args_exists(String name, int num_args) {
		for(Integer i: func_sig_indices) {
			FunctionInfo func_info = (FunctionInfo)(infos.get(i));	
			if(func_info.name.equals(name) && num_args == func_info.var_args.size())
				return true;
		}

		return false;
	}

	List<String> get_all_func_names() {
		List<String> li = new ArrayList<>();
		for(FuncNameArgs func_name_arg: func_name_args)
			li.add(func_name_arg.name);

		return li;
	}
}
