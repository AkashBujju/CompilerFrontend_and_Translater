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
	List<String> errors;
	List<Integer> func_sig_indices;
	List<RangeIndices> quotes_range_indices;
	List<FuncNameArgs> func_name_args;

	SemanticAnalyser(List<Info> infos, List<RangeIndices> quotes_range_indices) {
		this.infos = infos;
		this.quotes_range_indices = quotes_range_indices;
		errors = new ArrayList<>();
		func_sig_indices = new ArrayList<>();
		symbol_table = new SymbolTable();
		func_name_args = new ArrayList<>();

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
			String msg = "";
			if(info.info_type == InfoType.USE)
				msg = eval_use((UseInfo)(info));
			else if(info.info_type == InfoType.VAR_DECL)
				msg = eval_var_decl((VarDeclInfo)(info));
			else if(info.info_type == InfoType.FUNCTION)
				msg = eval_function((FunctionInfo)(info));

			if(!msg.equals("")) {
				errors.add(msg);
			}
		}

		for(String s: errors) {
			System.out.println(s);
			System.out.println();
		}
	}

	// @NotKnown: From where does the filepath start from ??????
	// @NotKnown: From where does the filepath start from ??????
	// @NotKnown: From where does the filepath start from ??????
	// @NotKnown: From where does the filepath start from ??????
	private String eval_use(UseInfo use_info) throws FileNotFoundException {
		String filename = use_info.filename;
		try {
			Scanner s = new Scanner(new BufferedReader(new FileReader(filename)));
		}
		catch(FileNotFoundException e) {
			return ErrorLog.get_msg("Could not find file: " + filename + ".", "use \"" + filename+ "\';", use_info.line_number);
		}

		return "";
	}

	private String eval_function(FunctionInfo func_info) {

		return "";
	}

	private String eval_var_decl(VarDeclInfo var_decl_info) {
		// @Incomplete: Check if the variable exists in the symbol table ???	

		String raw_value = var_decl_info.raw_value;
		List<String> split_value = Util.split_with_ops(raw_value);
		List<String> final_exp = new ArrayList<>();

		// System.out.println("\nsplit_value: ");

		int len = split_value.size();
		for(int i = 0; i < len; ++i) {
			String s = split_value.get(i);
			if(Util.is_operator(s)) {
				final_exp.add(s);
			}
			else {
				boolean if_func_call = Util.if_func_call(s);
				String type = "not_known";
				if(if_func_call) {
					type = get_type_of_func_call(s);	
					type = Util.get_primitive_literal(type);
				}
				else
					type = s; // Type is deduced later.

				final_exp.add(type);
			}
		}

		// @Note: The problem is an expression like '>> b' gets seperated into two, thereby get_type is called on '>>' and 'b' seperately which is incorrect....
		// IDEA: Replace the function return value with an appriopriate primitive literal. It may not always be reduced to a primitive type.

		// Applying all the operators(if any) on final_exp.
		// Converting to postfix.
		List<String> postfix_exp = InfixToPostFix.infixToPostFix(final_exp);
		EvalExp eval_exp = new EvalExp(postfix_exp);
		String type = eval_exp.deduce_final_type(symbol_table, "", 0);
		symbol_table.add(var_decl_info.name, type, raw_value);

		System.out.println("postfix <" + postfix_exp + ">, type <" + type + ">");
		// @NotDone
		// @NotDone
		// @NotDone
		// @NotDone
		// @NotDone

		return  ""; // @tmp
	}

	/*
	String get_type(String s) {
		boolean if_func_call = Util.if_func_call(s);
		String type = "not_known";

		if(if_func_call == true)
			return get_type_of_func_call(s);
		else
			type = get_type_of_exp(s);

		return s;
	}
	*/

	String get_type_of_exp(String s) {
		List<String> in_list = Util.split_with_ops(s);
		List<String> out_list = InfixToPostFix.infixToPostFix(in_list);

		EvalExp eval_exp = new EvalExp(out_list);
		String type = eval_exp.deduce_final_type(symbol_table, "", 0);
		// System.out.println("s: " + s + ", type: " + type);

		return type;
	}

	String get_type_of_func_call(String s) {
		/*
		 * Alg: 
		 * 1) Find the inner arguments of the function
		 * 2) Eval all exps for every inner argument
		 * 3) Replace the instances of the exp in the inner_arg with it's type
		 */

		List<String> args = Util.get_func_args(s);
		String func_name = s.substring(0, s.indexOf('('));
		boolean func_exists = func_with_num_args_exists(func_name, args.size());
		if(!func_exists) {
			System.out.println("Function with name '" + func_name + "' taking '" + args.size() + "' arguments dosen't exist.");
			return "not_known";
		}

		List<String> new_args = new ArrayList<>();
		String inner_arg = s.substring(s.indexOf('(') + 1, s.indexOf(')'));
		for(String arg: args) {
			List<String> exps = Util.get_only_exp(arg, get_all_func_names());
			List<String> funcs = Util.get_all_func_calls(arg);
			String new_arg = arg;

			for(String exp: exps) {
				String type = get_type_of_exp(exp);
				new_arg = Util.replace_in_str(new_arg, exp, type);
				inner_arg = Util.replace_in_str(inner_arg, exp, type);
			}

			new_args.add(new_arg);

			// System.out.println("new_arg <" + new_arg + ">");
		}

		// System.out.println("new_args: " + new_args);
		if(!Util.contains_func_call(inner_arg)) {
			StringBuffer final_s = new StringBuffer(inner_arg);
			final_s.insert(0, func_name + "(");
			final_s.append(")");
			String type = get_type_of_one_func_call(final_s.toString());
			// System.out.println("type <" + type + ">");

			return type;
		}

		List<String> evald_args = new ArrayList<>();
		for(String new_arg: new_args) {
			HashMap<String, String> hm = new HashMap<>();
			evald_args.add(iter_eval_until_no_funcs(new_arg, hm));
		}

		// System.out.println("evald_args <" + evald_args + ">");

		StringBuffer final_func_call = new StringBuffer(func_name + "(");
		int evald_args_len = evald_args.size();
		for(int i = 0; i < evald_args_len; ++i) {
			String evald_arg = evald_args.get(i);
			final_func_call.append(evald_arg);

			if(i != evald_args_len - 1)
				final_func_call.append(",");
		}

		final_func_call.append(")");
		// System.out.println("final_func_call <" + final_func_call + ">");

		return get_type_of_one_func_call(final_func_call.toString());
	}


	// Returns the inner argument of func, with all the function call evaluated.
	String iter_eval_until_no_funcs(String func, HashMap<String, String> hm) {
		String final_str =  func;
		String inner_arg = func.substring(func.indexOf('(') + 1, func.lastIndexOf(')'));

		List<String> all_funcs = Util.get_all_func_calls(inner_arg);

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
				type = get_type_of_one_func_call(f);
				hm.put(f, type);

				for(int j = i - 1; j >= 0; j--) {
					String j_ele = all_funcs.get(j);
					j_ele = Util.replace_in_str(j_ele, f, type);
					all_funcs.set(j, j_ele);

					// Keep replacing inner_arg with corresponding types untill, it contains no function calls.
					inner_arg = Util.replace_in_str(inner_arg, f, type);
					if(!Util.if_func_call(inner_arg)) {
						i = -1;
						break;
					}
				}
			}
		}

		// System.out.println("inner_arg <" + inner_arg + ">");
		StringBuffer final_func_call = new StringBuffer(func.substring(0, func.indexOf('(')));
		final_func_call.append("(" + inner_arg + ")");
		// System.out.println("final_func_call <" + final_func_call + ">");

		return get_type_of_one_func_call(final_func_call.toString());

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
	String get_type_of_one_func_call(String s) {
		String in_arg = s.substring(s.indexOf('(') + 1, s.lastIndexOf(')'));
		String name = s.substring(0, s.indexOf('('));
		List<String> arg_types = Util.get_func_args(s);

		FuncNameArgs func_name_arg = get_func_name_with_args(name, arg_types);
		if(func_name_arg == null)
			return "not_known";

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
